#define MINIAUDIO_IMPLEMENTATION
#include "miniaudio.h"

// A simple ring buffer structure to hold PCM data produced by Munt
// until miniaudio's data callback is ready to consume it.
#include <stdatomic.h>

typedef struct
{
    short* buffer;
    int capacity;
    atomic_int head;
    atomic_int tail;
    } RingBuffer;

typedef struct
{
    ma_device device;
    RingBuffer rb;
    int sampleRate;
    int channels;
} AudioEngineContext;

// This callback is fired by miniaudio when the OS audio driver needs more PCM data.
void data_callback(ma_device* pDevice, void* pOutput, const void* pInput, ma_uint32 frameCount)
{
    AudioEngineContext* ctx = (AudioEngineContext*)pDevice->pUserData;
    short* out = (short*)pOutput;
    int numSamplesRequested = frameCount * ctx->channels;

    int head = atomic_load_explicit(&ctx->rb.head, memory_order_acquire);
    int tail = atomic_load_explicit(&ctx->rb.tail, memory_order_relaxed);
    
    int availableSamples = head - tail;
    if (availableSamples < 0) {
        availableSamples += ctx->rb.capacity;
    }

    int samplesToRead = (availableSamples < numSamplesRequested) ? availableSamples : numSamplesRequested;

    for (int i = 0; i < samplesToRead; i++)
    {
        out[i] = ctx->rb.buffer[tail];
        tail = (tail + 1) % ctx->rb.capacity;
    }
    
    atomic_store_explicit(&ctx->rb.tail, tail, memory_order_release);

    // Removed ma_event_signal to avoid mutex lock in real-time audio callback

    if (samplesToRead < numSamplesRequested)
    {
        for (int i = samplesToRead; i < numSamplesRequested; i++)
        {
            out[i] = 0;
        }
    }
}

// ---------------------------------------------------------
// EXPORTED C API FOR JAVA FFM
// ---------------------------------------------------------

#if defined(_WIN32)
#define EXPORT __declspec(dllexport)
#else
#define EXPORT __attribute__((visibility("default")))
#endif

#ifdef __cplusplus
extern "C"
{
#endif

    EXPORT AudioEngineContext* midiraja_audio_init(int sampleRate, int channels,
                                                   int bufferSizeInFrames)
    {
        AudioEngineContext* ctx = (AudioEngineContext*)malloc(sizeof(AudioEngineContext));
        if (!ctx) return NULL;

        ctx->sampleRate = sampleRate;
        ctx->channels = channels;

        // Initialize Ring Buffer (capable of holding bufferSizeInFrames)
        // Add 1 to capacity since head == tail means empty, so we can only store capacity - 1 elements.
        ctx->rb.capacity = (bufferSizeInFrames * channels) + 1; 
        ctx->rb.buffer = (short*)malloc(sizeof(short) * ctx->rb.capacity);
        atomic_init(&ctx->rb.head, 0);
        atomic_init(&ctx->rb.tail, 0);

                // Initialize Miniaudio Device
        ma_device_config deviceConfig = ma_device_config_init(ma_device_type_playback);
        deviceConfig.playback.format = ma_format_s16;  // Short (16-bit)
        deviceConfig.playback.channels = channels;
        deviceConfig.sampleRate = sampleRate;
        deviceConfig.dataCallback = data_callback;
        deviceConfig.pUserData = ctx;

        if (ma_device_init(NULL, &deviceConfig, &ctx->device) != MA_SUCCESS)
        {
            printf("[NativeAudio] Failed to init miniaudio device.\n");
                        free(ctx->rb.buffer);
            free(ctx);
            return NULL;
        }

        if (ma_device_start(&ctx->device) != MA_SUCCESS)
        {
            printf("[NativeAudio] Failed to start miniaudio device.\n");
            ma_device_uninit(&ctx->device);
                        free(ctx->rb.buffer);
            free(ctx);
            return NULL;
        }

        // printf("[NativeAudio] Engine started: %d Hz, %d channels\n", sampleRate, channels);

        return ctx;
    }

    EXPORT int midiraja_audio_get_queued_frames(AudioEngineContext* ctx)
    {
        if (!ctx) return 0;
        
        int head = atomic_load_explicit(&ctx->rb.head, memory_order_acquire);
        int tail = atomic_load_explicit(&ctx->rb.tail, memory_order_acquire);
        
        int count = head - tail;
        if (count < 0) {
            count += ctx->rb.capacity;
        }
        
        return count / ctx->channels;
    }

    // Returns the total device-side latency in frames at ctx->sampleRate (32kHz).
    // Covers miniaudio's internal period buffer + CoreAudio hardware/stream/safety latency.
    EXPORT int midiraja_audio_get_device_latency_frames(AudioEngineContext* ctx)
    {
        if (!ctx) return 0;

        // miniaudio internal pipeline: internalPeriodSizeInFrames is at the device's
        // internalSampleRate, not at ctx->sampleRate. Scale to ctx->sampleRate.
        int miniaudioFrames = 0;
        {
            double internalRate = (double)ctx->device.playback.internalSampleRate;
            if (internalRate > 0.0)
            {
                double rawFrames = (double)(ctx->device.playback.internalPeriodSizeInFrames *
                                            ctx->device.playback.internalPeriods);
                miniaudioFrames = (int)(rawFrames * ctx->sampleRate / internalRate);
            }
        }

#if defined(__APPLE__)
        // Also query CoreAudio hardware device latency + stream latency + safety offset.
        // All three are needed for complete end-to-end latency on macOS.
        AudioObjectID deviceID = ctx->device.coreaudio.deviceObjectIDPlayback;
        if (deviceID != 0)
        {
            UInt32 sz;

            // 1. Device hardware latency (frames at hw nominal rate)
            AudioObjectPropertyAddress addr = {kAudioDevicePropertyLatency,
                                               kAudioObjectPropertyScopeOutput,
                                               kAudioObjectPropertyElementMain};
            UInt32 devLatency = 0;
            sz = sizeof(devLatency);
            AudioObjectGetPropertyData(deviceID, &addr, 0, NULL, &sz, &devLatency);

            // 2. Safety offset: additional lead-time the OS requires to avoid underruns
            AudioObjectPropertyAddress safetyAddr = {kAudioDevicePropertySafetyOffset,
                                                     kAudioObjectPropertyScopeOutput,
                                                     kAudioObjectPropertyElementMain};
            UInt32 safetyOffset = 0;
            sz = sizeof(safetyOffset);
            AudioObjectGetPropertyData(deviceID, &safetyAddr, 0, NULL, &sz, &safetyOffset);
            devLatency += safetyOffset;

            // 3. Sum latency of all output streams
            AudioObjectPropertyAddress streamsAddr = {kAudioDevicePropertyStreams,
                                                      kAudioObjectPropertyScopeOutput,
                                                      kAudioObjectPropertyElementMain};
            UInt32 streamsSize = 0;
            if (AudioObjectGetPropertyDataSize(deviceID, &streamsAddr, 0, NULL, &streamsSize) ==
                    noErr &&
                streamsSize > 0)
            {
                AudioStreamID* streams = (AudioStreamID*)malloc(streamsSize);
                if (streams)
                {
                    if (AudioObjectGetPropertyData(deviceID, &streamsAddr, 0, NULL, &streamsSize,
                                                   streams) == noErr)
                    {
                        AudioObjectPropertyAddress streamLatAddr = {
                            kAudioStreamPropertyLatency, kAudioObjectPropertyScopeOutput,
                            kAudioObjectPropertyElementMain};
                        UInt32 streamLat = 0;
                        sz = sizeof(streamLat);
                        AudioObjectGetPropertyData(streams[0], &streamLatAddr, 0, NULL, &sz,
                                                   &streamLat);
                        devLatency += streamLat;
                    }
                    free(streams);
                }
            }

            // devLatency is in frames at the hardware's nominal sample rate. Scale to
            // ctx->sampleRate.
            Float64 hwRate = 0.0;
            AudioObjectPropertyAddress rateAddr = {kAudioDevicePropertyNominalSampleRate,
                                                   kAudioObjectPropertyScopeOutput,
                                                   kAudioObjectPropertyElementMain};
            sz = sizeof(hwRate);
            AudioObjectGetPropertyData(deviceID, &rateAddr, 0, NULL, &sz, &hwRate);
            if (hwRate > 0.0)
            {
                devLatency = (UInt32)((devLatency * ctx->sampleRate) / hwRate);
            }

            miniaudioFrames += (int)devLatency;
        }
#endif

        return miniaudioFrames;
    }

    EXPORT int midiraja_audio_push(AudioEngineContext* ctx, const short* data, int numSamples)
    {
        if (!ctx || !data || numSamples <= 0) return 0;

        int head = atomic_load_explicit(&ctx->rb.head, memory_order_relaxed);
        int tail = atomic_load_explicit(&ctx->rb.tail, memory_order_acquire);
        
        int count = head - tail;
        if (count < 0) {
            count += ctx->rb.capacity;
        }
        
        int availableSpace = (ctx->rb.capacity - 1) - count;
        int samplesToWrite = (availableSpace < numSamples) ? availableSpace : numSamples;

        if (samplesToWrite > 0)
        {
            for (int i = 0; i < samplesToWrite; i++)
            {
                ctx->rb.buffer[head] = data[i];
                head = (head + 1) % ctx->rb.capacity;
            }
            atomic_store_explicit(&ctx->rb.head, head, memory_order_release);
        }
        
        return samplesToWrite;
    }

    // Atomically discards all queued frames from the ring buffer and wakes the render
    // thread if it is blocked waiting for space. Call this after sending a reverb-off
    // SysEx and waiting for Munt to process it, so the next song starts with a silent buffer.
    
    EXPORT int midiraja_audio_get_buffer_capacity_frames(AudioEngineContext* ctx)
    {
        if (!ctx) return 0;
        return (ctx->rb.capacity - 1) / ctx->channels;
    }

    EXPORT void midiraja_audio_flush(AudioEngineContext* ctx)
    {
        if (!ctx) return;
        atomic_store_explicit(&ctx->rb.head, 0, memory_order_relaxed);
        atomic_store_explicit(&ctx->rb.tail, 0, memory_order_release);
        
        // Wake the render thread if it is blocked in midiraja_audio_push() waiting for space.
            }

    EXPORT void midiraja_audio_close(AudioEngineContext* ctx)
    {
        if (!ctx) return;

        ma_device_uninit(&ctx->device);
                free(ctx->rb.buffer);
        free(ctx);
    }

#ifdef __cplusplus
}
#endif