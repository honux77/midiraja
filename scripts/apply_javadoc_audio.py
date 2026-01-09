import sys

with open('src/main/java/com/midiraja/midi/AudioEngine.java', 'r') as f:
    text = f.read()

text = text.replace('public interface AudioEngine extends AutoCloseable', """/**
 * Abstraction for an audio output engine.
 * Decouples MIDI synthesis providers from the native FFM/JNI audio drivers (DIP principle).
 */
public interface AudioEngine extends AutoCloseable""")

text = text.replace('    void init(int sampleRate, int channels, int bufferSize) throws Exception;', """    /**
     * Initializes the audio device with the specified parameters.
     * @param sampleRate Sampling rate in Hz (e.g., 44100).
     * @param channels Number of audio channels (1 for Mono, 2 for Stereo).
     * @param bufferSize Size of the internal audio buffer in frames.
     */
    void init(int sampleRate, int channels, int bufferSize) throws Exception;""")

text = text.replace('    void push(short[] pcm);', """    /**
     * Pushes a buffer of PCM samples to the audio device.
     * @param pcm Array of short PCM samples.
     */
    void push(short[] pcm);""")

with open('src/main/java/com/midiraja/midi/AudioEngine.java', 'w') as f:
    f.write(text)

