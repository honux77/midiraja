
#define MINIAUDIO_IMPLEMENTATION
#include "miniaudio.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// Ring buffer capacity in frames (at 44100 Hz stereo, ~185ms of headroom)
#define AUDIO_RING_BUFFER_FRAMES 16384

typedef struct
{
    ma_device device;
    ma_pcm_rb rb;
    int sampleRate;
    int channels;
} AudioEngineContext;

// Called by miniaudio on the OS audio thread whenever it needs more PCM data.
// Reads from the C-side ring buffer; outputs silence if no data is available.
void data_callback(ma_device* pDevice, void* pOutput, const void* pInput, ma_uint32 frameCount)
{
    AudioEngineContext* ctx = (AudioEngineContext*)pDevice->pUserData;
    short* out = (short*)pOutput;

    ma_uint32 framesAvailable = frameCount;
    void* readPtr;
    if (ma_pcm_rb_acquire_read(&ctx->rb, &framesAvailable, &readPtr) == MA_SUCCESS
            && framesAvailable > 0)
    {
        memcpy(out, readPtr, framesAvailable * (ma_uint32)ctx->channels * sizeof(short));
        ma_pcm_rb_commit_read(&ctx->rb, framesAvailable);

        // Pad remainder with silence if ring buffer ran short
        if (framesAvailable < frameCount)
        {
            memset((short*)out + framesAvailable * (ma_uint32)ctx->channels, 0,
                    (frameCount - framesAvailable) * (ma_uint32)ctx->channels * sizeof(short));
        }
    }
    else
    {
        memset(out, 0, frameCount * (ma_uint32)ctx->channels * sizeof(short));
    }
}

#ifdef _WIN32
#define EXPORT __declspec(dllexport)
#else
#define EXPORT __attribute__((visibility("default")))
#endif

// Initializes the audio engine and starts playback.
// Java pushes PCM data via midiraja_audio_push(); the OS audio thread reads from the ring buffer.
EXPORT AudioEngineContext* midiraja_audio_init(int sampleRate, int channels,
        int bufferSizeInFrames)
{
    AudioEngineContext* ctx = (AudioEngineContext*)malloc(sizeof(AudioEngineContext));
    if (!ctx)
        return NULL;

    ctx->sampleRate = sampleRate;
    ctx->channels = channels;

    if (ma_pcm_rb_init(ma_format_s16, (ma_uint32)channels, AUDIO_RING_BUFFER_FRAMES, NULL, NULL,
                &ctx->rb)
            != MA_SUCCESS)
    {
        free(ctx);
        return NULL;
    }

    ma_device_config deviceConfig = ma_device_config_init(ma_device_type_playback);
    deviceConfig.playback.format = ma_format_s16;
    deviceConfig.playback.channels = (ma_uint32)channels;
    deviceConfig.sampleRate = (ma_uint32)sampleRate;
    deviceConfig.dataCallback = data_callback;
    deviceConfig.pUserData = ctx;
    deviceConfig.periodSizeInFrames = 512;

    if (ma_device_init(NULL, &deviceConfig, &ctx->device) != MA_SUCCESS)
    {
        printf("[NativeAudio] Failed to init miniaudio device.\n");
        ma_pcm_rb_uninit(&ctx->rb);
        free(ctx);
        return NULL;
    }

    if (ma_device_start(&ctx->device) != MA_SUCCESS)
    {
        printf("[NativeAudio] Failed to start miniaudio device.\n");
        ma_device_uninit(&ctx->device);
        ma_pcm_rb_uninit(&ctx->rb);
        free(ctx);
        return NULL;
    }

    return ctx;
}

// Pushes frameCount frames of PCM data (short[]) into the ring buffer.
// Returns the number of frames actually written (may be less if the buffer is nearly full).
EXPORT int midiraja_audio_push(AudioEngineContext* ctx, void* buffer, int frameCount)
{
    if (!ctx || !buffer || frameCount <= 0)
        return 0;

    ma_uint32 framesToWrite = (ma_uint32)frameCount;
    void* writePtr;
    if (ma_pcm_rb_acquire_write(&ctx->rb, &framesToWrite, &writePtr) != MA_SUCCESS
            || framesToWrite == 0)
        return 0;

    memcpy(writePtr, buffer, framesToWrite * (ma_uint32)ctx->channels * sizeof(short));
    ma_pcm_rb_commit_write(&ctx->rb, framesToWrite);
    return (int)framesToWrite;
}

// Returns the number of frames currently available in the ring buffer.
EXPORT int midiraja_audio_get_queued_frames(AudioEngineContext* ctx)
{
    if (!ctx)
        return 0;
    return (int)ma_pcm_rb_available_read(&ctx->rb);
}

// Clears all pending audio in the ring buffer (e.g. on seek or track change).
EXPORT void midiraja_audio_flush(AudioEngineContext* ctx)
{
    if (!ctx)
        return;
    ma_pcm_rb_reset(&ctx->rb);
}

// Returns the total device-side latency in frames (currently not measured).
EXPORT int midiraja_audio_get_device_latency_frames(AudioEngineContext* ctx)
{
    if (!ctx)
        return 0;
    return 0;
}

EXPORT void midiraja_audio_close(AudioEngineContext* ctx)
{
    if (!ctx)
        return;
    ma_device_uninit(&ctx->device);
    ma_pcm_rb_uninit(&ctx->rb);
    free(ctx);
}
