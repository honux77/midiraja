import sys

with open('src/main/java/com/midiraja/midi/beep/BeepSynthProvider.java', 'r') as f:
    text = f.read()

# Class Javadoc
class_desc = """/**
 * A purist 1-bit digital cluster soft-synth provider.
 * 
 * <p>This engine emulates the extreme physical limitations of 1980s microcomputers (like the Apple II)
 * by enforcing a strict boolean-only signal path. It supports various synthesis methods (Square, XOR, FM)
 * and digital multiplexing strategies (XOR collision, TDM time-slicing).
 * 
 * <p>Key features include:
 * <ul>
 *   <li>100% Fixed-Point 16-bit integer math for synthesis.</li>
 *   <li>8-bit signed integer Sine Look-Up Table (LUT).</li>
 *   <li>Internal PWM quantization for continuous waveforms.</li>
 *   <li>Psychoacoustic Frequency-Weighted Bass Isolation routing.</li>
 *   <li>AI-tuned DSP parameters via the 'God Table' lookup matrix.</li>
 * </ul>
 */
@SuppressWarnings({"ThreadPriorityCheck", "EmptyCatch"})"""

text = text.replace('@SuppressWarnings({"ThreadPriorityCheck", "EmptyCatch"})', class_desc)

# DspParams Javadoc
text = text.replace('    private static class DspParams\n    {', """    /**
     * Internal container for AI-optimized DSP parameters.
     */
    private static class DspParams
    {""")

# GOD_TABLE Javadoc
text = text.replace('    private static final java.util.Map<String, DspParams> GOD_TABLE =', """    /**
     * The God Table: A static lookup matrix of AI-tuned DSP parameters.
     * Contains LPF cutoffs, dither amplitudes, and overdrive levels discovered
     * via Genetic Algorithm for every combination of synth and mux modes.
     */
    private static final java.util.Map<String, DspParams> GOD_TABLE =""")

# ActiveNote Javadoc
text = text.replace('    private static class ActiveNote\n    {', """    /**
     * Represents a single active MIDI note in the synthesis engine.
     * Uses 16-bit fixed-point accumulators for hardware-accurate phase tracking.
     */
    private static class ActiveNote
    {""")

# fastSinInt Javadoc
text = text.replace('    @SuppressWarnings("unused")\n    private static int fastSinInt(int phase16)\n    {', """    /**
     * Per-sample 16-bit fixed-point Sine lookup.
     * 
     * @param phase16 The 16-bit phase accumulator (0-65535).
     * @return The 8-bit signed amplitude (-127 to +127).
     */
    @SuppressWarnings("unused")
    private static int fastSinInt(int phase16)
    {""")

# DigitalUnit Javadoc
text = text.replace('    private class DigitalUnit\n    {', """    /**
     * A virtual 1-bit processing unit representing a single physical speaker output pin.
     * Multiple units are combined via analog summation to reach modern playback volumes.
     */
    private class DigitalUnit
    {""")

# DigitalUnit.render Javadoc
text = text.replace('        double render(List<ActiveNote> assignedNotes)\n        {', """        /**
         * Renders 1-bit audio for the notes assigned to this specific unit.
         * 
         * @param assignedNotes The list of active notes assigned to this unit.
         * @return The resulting 1-bit signal normalized to [-1.0, 1.0].
         */
        double render(List<ActiveNote> assignedNotes)
        {""")

# Constructor Javadoc
text = text.replace('    public BeepSynthProvider(AudioEngine audio, int voices,', """    /**
     * Constructs a new 1-Bit Digital Cluster provider.
     * 
     * @param audio The audio engine interface for output.
     * @param voices Number of voices per core/unit (1-4).
     * @param fmRatio Modulator frequency ratio for FM synthesis.
     * @param fmIndex Modulation intensity for FM synthesis.
     * @param oversample Oversampling factor (e.g., 32x).
     * @param muxMode Multiplexing algorithm ("xor" or "tdm").
     * @param synthMode Synthesis algorithm ("fm", "xor", or "square").
     */
    public BeepSynthProvider(AudioEngine audio, int voices,""")

# renderCluster Javadoc
text = text.replace('    private void renderCluster(List<ActiveNote> notes, short[] buffer, int frames)\n    {', """    /**
     * Performs frequency-weighted note routing and triggers parallel unit rendering.
     * Implements "Bass Isolation" to prevent muddy intermodulation in the lower spectrum.
     * 
     * @param notes Snapshot of currently active MIDI notes.
     * @param buffer The output PCM buffer to fill.
     * @param frames Number of frames to render.
     */
    private void renderCluster(List<ActiveNote> notes, short[] buffer, int frames)
    {""")

with open('src/main/java/com/midiraja/midi/beep/BeepSynthProvider.java', 'w') as f:
    f.write(text)

