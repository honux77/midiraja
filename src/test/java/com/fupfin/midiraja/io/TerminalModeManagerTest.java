/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.fupfin.midiraja.io;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;

class TerminalModeManagerTest
{
    @Test
    void enterRawNoIsig_disablesIsigAndEcho() throws IOException
    {
        try (Terminal terminal = TerminalBuilder.builder().dumb(true).build())
        {
            TerminalModeManager.enterRawNoIsig(terminal);

            Attributes attr = terminal.getAttributes();
            assertFalse(attr.getLocalFlag(Attributes.LocalFlag.ISIG),
                    "ISIG must be disabled so Ctrl+C is delivered as ETX, not SIGINT");
            assertFalse(attr.getLocalFlag(Attributes.LocalFlag.ECHO),
                    "ECHO must be disabled in raw mode");
        }
    }
}
