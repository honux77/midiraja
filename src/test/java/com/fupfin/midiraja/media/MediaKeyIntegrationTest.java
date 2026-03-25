/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.fupfin.midiraja.media;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MediaKeyIntegrationTest
{
    @Test void nowPlayingInfo_equality()
    {
        var a = new NowPlayingInfo("Song", "Artist", 60_000_000L, 5_000_000L, true);
        var b = new NowPlayingInfo("Song", "Artist", 60_000_000L, 5_000_000L, true);
        assertEquals(a, b);
    }

    @Test void nowPlayingInfo_emptyArtist()
    {
        var info = new NowPlayingInfo("Song", "", 0L, 0L, false);
        assertEquals("", info.artist());
    }

    @Test void noOp_allMethodsAreNoOp()
    {
        var noOp = NoOpMediaIntegration.INSTANCE;
        assertDoesNotThrow(() -> noOp.start(null));
        assertDoesNotThrow(() -> noOp.drainAndUpdate(new NowPlayingInfo("T", "", 0, 0, true)));
        assertDoesNotThrow(() -> noOp.close());
        assertDoesNotThrow(NoOpMediaIntegration.INSTANCE::close); // close before start
    }

    @Test void noOp_drainAndUpdate_beforeStart_isNoOp()
    {
        assertDoesNotThrow(() ->
            NoOpMediaIntegration.INSTANCE.drainAndUpdate(
                new NowPlayingInfo("T", "", 1_000_000L, 0L, true)));
    }

    @Test void create_returnsNonNull()
    {
        assertNotNull(MediaKeyIntegration.create());
    }
}
