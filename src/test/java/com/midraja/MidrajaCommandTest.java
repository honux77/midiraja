package com.midraja;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.ParseResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MidrajaCommandTest {

    @Test
    void testParseVolumeOption() {
        MidrajaCommand app = new MidrajaCommand();
        CommandLine cmd = new CommandLine(app);
        
        ParseResult parseResult = cmd.parseArgs("--volume", "64", "--port", "1", "song.mid");
        
        assertTrue(parseResult.hasMatchedOption("--volume"));
        assertEquals(Integer.valueOf(64), parseResult.matchedOption("--volume").getValue());
    }

    @Test
    void testParseTransposeOption() {
        MidrajaCommand app = new MidrajaCommand();
        CommandLine cmd = new CommandLine(app);
        
        ParseResult parseResult = cmd.parseArgs("-t", "-5", "--port", "1", "song.mid");
        
        assertTrue(parseResult.hasMatchedOption("-t"));
        assertEquals(Integer.valueOf(-5), parseResult.matchedOption("-t").getValue());
    }

    @Test
    void testInvalidVolumeOutOfRange() {
        MidrajaCommand app = new MidrajaCommand();
        CommandLine cmd = new CommandLine(app);
        
        java.io.ByteArrayOutputStream errContent = new java.io.ByteArrayOutputStream();
        java.io.PrintStream originalErr = System.err;
        System.setErr(new java.io.PrintStream(errContent));
        
        try {
            int exitCode = cmd.execute("--volume", "150", "--port", "1", "song.mid");
            assertTrue(exitCode != 0);
            assertTrue(errContent.toString().contains("Volume must be between 0 and 127"));
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    void testInvalidVolumeNegative() {
        MidrajaCommand app = new MidrajaCommand();
        CommandLine cmd = new CommandLine(app);
        
        java.io.ByteArrayOutputStream errContent = new java.io.ByteArrayOutputStream();
        java.io.PrintStream originalErr = System.err;
        System.setErr(new java.io.PrintStream(errContent));
        
        try {
            int exitCode = cmd.execute("--volume", "-1", "--port", "1", "song.mid");
            assertTrue(exitCode != 0);
            assertTrue(errContent.toString().contains("Volume must be between 0 and 127"));
        } finally {
            System.setErr(originalErr);
        }
    }
}
