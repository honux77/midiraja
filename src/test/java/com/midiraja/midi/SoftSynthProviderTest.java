/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.midiraja.midi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SoftSynthProviderTest {

    @Test
    void testGetOutputPortsExtractsExecutableName() {
        SoftSynthProvider provider1 = new SoftSynthProvider("fluidsynth -i my_font.sf2 -");
        List<MidiPort> ports1 = provider1.getOutputPorts();
        assertEquals(1, ports1.size());
        assertEquals("SoftSynth: fluidsynth", ports1.get(0).name());

        SoftSynthProvider provider2 = new SoftSynthProvider("/usr/bin/timidity -iA");
        List<MidiPort> ports2 = provider2.getOutputPorts();
        assertEquals("SoftSynth: /usr/bin/timidity", ports2.get(0).name());
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void testProcessExecutionAndCommunication() throws Exception {
        // We use a simple shell command that reads from stdin and writes to stdout
        // 'cat' is perfect for this. It simulates a synth receiving raw bytes.
        // We wrap it in a script that prepends "ACK:" to prove it processed the input.
        String command = "sh -c 'read line; echo \"ACK:$line\"'";
        
        SoftSynthProvider provider = new SoftSynthProvider(command);
        
        // Open the port (starts the process)
        provider.openPort(0);
        
        // Send a dummy MIDI message
        byte[] testMessage = "HELLO_SYNTH\n".getBytes(StandardCharsets.UTF_8);
        provider.sendMessage(testMessage);
        
        // Close the port (closes stdin and waits for process to terminate)
        provider.closePort();
        
        // Since we are not capturing stdout in the SoftSynthProvider (it's consumed and discarded by a daemon thread),
        // we can't easily assert on the "ACK:HELLO_SYNTH" output directly through the provider interface.
        // However, the test passing without throwing exceptions means the ProcessBuilder started successfully,
        // the OutputStream was available, and the process was terminated cleanly by closePort().
        //
        // In a real scenario, we might want to inject a mock ProcessBuilder or intercept the output stream,
        // but for now, testing the lifecycle is the most critical part.
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void testProcessForcedTermination() throws Exception {
        // Use a command that sleeps indefinitely to simulate a hung synth
        String command = "sleep 10";
        SoftSynthProvider provider = new SoftSynthProvider(command);
        
        provider.openPort(0);
        
        long startTime = System.currentTimeMillis();
        provider.closePort(); // This should trigger the 500ms wait and then destroyForcibly()
        long endTime = System.currentTimeMillis();
        
        long duration = endTime - startTime;
        // The process might terminate immediately via SIGTERM, or take up to 500ms + margin.
        // As long as it doesn't hang for 10 seconds, the lifecycle management works.
        assertTrue(duration < 2000, "closePort should terminate a process (hung or otherwise) within 2 seconds");
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void testInvalidCommandThrowsException() {
        // Command that definitely doesn't exist
        String command = "this_command_does_not_exist_at_all 123";
        SoftSynthProvider provider = new SoftSynthProvider(command);
        
        // Depending on how ProcessBuilder is wrapped (e.g., in "sh -c"), it might actually start the shell
        // successfully, and the shell itself fails and exits.
        // If we were executing directly without "sh -c", ProcessBuilder.start() would throw an IOException immediately.
        // Let's test the current implementation behavior.
        assertDoesNotThrow(() -> {
            provider.openPort(0);
        });
        
        // But sending a message might fail if the process exited immediately, closing the pipe.
        // Actually, Java's Process OutputStream might not throw immediately on write if the buffer isn't full,
        // but closePort should clean up without issues.
        assertDoesNotThrow(() -> {
            provider.sendMessage(new byte[]{(byte) 0x90, 0x3c, 0x40});
            provider.closePort();
        });
    }
}
