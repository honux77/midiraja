/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.midiraja.midi;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FluidSynthProviderTest {

    @Test
    void testInitializationWithoutLibraryThrowsHelpfulException() {
        // This test simulates an environment where libfluidsynth is not installed.
        // Since we cannot easily "unload" a library if it is present on the developer's machine,
        // we test the fallback mechanism. If the library IS present, it should load cleanly.
        // If NOT, it should throw an informative Exception, not just a raw UnsatisfiedLinkError.
        
        try {
            FluidSynthProvider provider = new FluidSynthProvider(null);
            assertNotNull(provider);
        } catch (Exception e) {
            assertTrue(e.getMessage().toLowerCase().contains("fluidsynth"), 
                "Exception message should mention FluidSynth");
            assertTrue(e.getMessage().toLowerCase().contains("install"), 
                "Exception message should provide a hint to install it");
        }
    }
}