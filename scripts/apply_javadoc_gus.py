import sys

with open('src/main/java/com/midiraja/midi/gus/GusSynthProvider.java', 'r') as f:
    text = f.read()

text = text.replace('public class GusSynthProvider implements SoftSynthProvider', """/**
 * A Gravis Ultrasound (GUS) soft-synth provider.
 * 
 * <p>This engine emulates the classic GF1 wavetable synthesis and supports
 * authentic retro audio features like RealSound (6-bit PWM) and modular DSP filters.
 */
public class GusSynthProvider implements SoftSynthProvider""")

text = text.replace('    public GusSynthProvider(AudioEngine audio, @Nullable String patchDir)', """    /**
     * Constructs a standard 16-bit GUS provider.
     * @param audio The audio engine interface.
     * @param patchDir Path to the GUS patch directory (containing gus.cfg).
     */
    public GusSynthProvider(AudioEngine audio, @Nullable String patchDir)""")

text = text.replace('    public GusSynthProvider(AudioEngine audio, @Nullable String patchDir, int bitDepth, boolean pwmMode)', """    /**
     * Constructs a customizable GUS provider with bit-crushing and PWM options.
     * @param audio The audio engine interface.
     * @param patchDir Path to the GUS patch directory.
     * @param bitDepth Internal rendering bit depth (1-16).
     * @param pwmMode Whether to enable Pulse Width Modulation (RealSound) output.
     */
    public GusSynthProvider(AudioEngine audio, @Nullable String patchDir, int bitDepth, boolean pwmMode)""")

with open('src/main/java/com/midiraja/midi/gus/GusSynthProvider.java', 'w') as f:
    f.write(text)

