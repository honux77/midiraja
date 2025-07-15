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
}
