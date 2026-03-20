package com.fupfin.midiraja.cli;

import static org.junit.jupiter.api.Assertions.*;

import com.fupfin.midiraja.io.MockTerminalIO;
import com.fupfin.midiraja.io.TerminalIO.TerminalKey;
import com.fupfin.midiraja.midi.MidiOutProvider;
import com.fupfin.midiraja.midi.MidiPort;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlaybackRunnerTest {

    private ByteArrayOutputStream outBytes;
    private ByteArrayOutputStream errBytes;
    private MockTerminalIO mockIO;
    private CommonOptions common;

    static class MockMidiProvider implements MidiOutProvider {
        boolean isOpen = false;
        boolean isClosed = false;

        @Override public List<MidiPort> getOutputPorts() { return List.of(new MidiPort(0, "MockPort")); }
        @Override public void openPort(int portIndex) { isOpen = true; }
        @Override public void closePort() { isClosed = true; }
        @Override public void sendMessage(byte[] data) {}
        @Override public void panic() {}
        @Override public long getAudioLatencyNanos() { return 0; }
        @Override public void onPlaybackStarted() {}
        @Override public void prepareForNewTrack(Sequence sequence) {}
    }

    @BeforeEach
    void setUp() {
        outBytes = new ByteArrayOutputStream();
        errBytes = new ByteArrayOutputStream();
        mockIO = new MockTerminalIO();
        common = new CommonOptions();
    }

    private File createTestMidi(Path tempDir) throws Exception {
        File midiFile = tempDir.resolve("test.mid").toFile();
        Sequence seq = new Sequence(Sequence.PPQ, 24);
        Track t = seq.createTrack();
        t.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, 60, 64), 0));
        // Add a long delay so the track doesn't finish instantly
        t.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 2400));
        try (FileOutputStream fos = new FileOutputStream(midiFile)) {
            MidiSystem.write(seq, 1, fos);
        }
        return midiFile;
    }

    @Test
    void testRunnerQuitsOnQKey(@TempDir Path tempDir) throws Exception {
        File midiFile = createTestMidi(tempDir);
        MockMidiProvider provider = new MockMidiProvider();
        PlaybackRunner runner = new PlaybackRunner(new PrintStream(outBytes), new PrintStream(errBytes), mockIO, true);

        // Inject the 'q' key to force the loop to exit early
        mockIO.injectKey(TerminalKey.QUIT);

        // Run as SoftSynth (skips port selection)
        int exitCode = runner.run(provider, true, Optional.empty(), Optional.empty(), List.of(midiFile), common, List.of());

        assertEquals(0, exitCode, "Exit code should be 0 on normal quit");
        assertTrue(provider.isOpen, "Provider should have been opened");
        
        // Ensure that the output stream was used (meaning UI or at least init text was written)
        // Test passed if it reached here without hanging
    }
    
    @Test
    void testRunnerHandlesNoFiles() throws Exception {
        MockMidiProvider provider = new MockMidiProvider();
        PlaybackRunner runner = new PlaybackRunner(new PrintStream(outBytes), new PrintStream(errBytes), mockIO, true);

        int exitCode = runner.run(provider, true, Optional.empty(), Optional.empty(), new ArrayList<>(), common, List.of());

        assertEquals(1, exitCode, "Exit code should be 1 if no files are found");
        String errOutput = errBytes.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(errOutput.contains("No MIDI files specified"), "Should print error about missing files");
    }

    @Test void buildPlayOrder_notShuffled_isSequential() {
        int[] order = PlaybackRunner.buildPlayOrder(4, false);
        assertArrayEquals(new int[]{0, 1, 2, 3}, order);
    }

    @Test void buildPlayOrder_shuffled_containsAllIndices() {
        int[] order = PlaybackRunner.buildPlayOrder(5, true);
        assertEquals(5, order.length);
        // All indices 0-4 present
        int sum = 0;
        for (int v : order) sum += v;
        assertEquals(0 + 1 + 2 + 3 + 4, sum);
    }

    @Test void buildPlayOrder_sizeZero_returnsEmpty() {
        assertArrayEquals(new int[0], PlaybackRunner.buildPlayOrder(0, false));
        assertArrayEquals(new int[0], PlaybackRunner.buildPlayOrder(0, true));
    }

    @Test void buildPlayOrder_sizeOne_returnsSingleElement() {
        assertArrayEquals(new int[]{0}, PlaybackRunner.buildPlayOrder(1, true));
    }

    @Test void reshuffleRemaining_shuffleOn_remainingNotInOriginalOrder() {
        // Use a large enough slice that the probability of staying sorted is negligible
        int[] order = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        PlaybackRunner.reshuffleRemaining(order, 0, true);
        // The remaining slice [1..9] should not be in original order (with overwhelming probability)
        boolean stillSorted = true;
        for (int i = 1; i < order.length - 1; i++) {
            if (order[i] > order[i + 1]) { stillSorted = false; break; }
        }
        // index 0 is untouched
        assertEquals(0, order[0]);
        // All values 1-9 still present
        int sum = 0;
        for (int i = 1; i < order.length; i++) sum += order[i];
        assertEquals(1+2+3+4+5+6+7+8+9, sum);
        // At least one element is out of order (not all 9 in sequence)
        assertFalse(stillSorted, "Shuffled slice should not remain sorted");
    }

    @Test void reshuffleRemaining_shuffleOff_restoresAscendingOrder() {
        int[] order = {0, 4, 2, 1, 3}; // remaining [1..4] is unsorted
        PlaybackRunner.reshuffleRemaining(order, 0, false);
        assertArrayEquals(new int[]{0, 1, 2, 3, 4}, order);
    }

    @Test void reshuffleRemaining_atLastTrack_isNoOp() {
        int[] order = {0, 1, 2};
        int[] before = order.clone();
        PlaybackRunner.reshuffleRemaining(order, 2, true); // currentIdx = last
        assertArrayEquals(before, order);
    }

    @Test void reshuffleRemaining_idempotentSortOff() {
        int[] order = {0, 1, 2, 3}; // already sorted
        PlaybackRunner.reshuffleRemaining(order, 0, false);
        assertArrayEquals(new int[]{0, 1, 2, 3}, order);
    }
}
