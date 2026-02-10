/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.fupfin.midiraja.midi;

import java.util.List;
import javax.sound.midi.*;
import org.jspecify.annotations.Nullable;

/**
 * A proof-of-concept provider that uses Java's built-in software synthesizer (Gervill).
 * Does not depend on OS-native FFM integrations.
 */
public class JavaSynthProvider implements MidiOutProvider
{
    @Nullable private Synthesizer synth;
    @Nullable private Receiver receiver;

    @Override public List<MidiPort> getOutputPorts()
    {
        return List.of(new MidiPort(0, "Java Built-in Synthesizer"));
    }

    @Override public void openPort(int portIndex) throws Exception
    {
        synth = MidiSystem.getSynthesizer();
        synth.open();
        receiver = synth.getReceiver();

        // Load default soundbank if available
        Soundbank defaultSoundbank = synth.getDefaultSoundbank();
        if (defaultSoundbank != null)
        {
            synth.loadAllInstruments(defaultSoundbank);
        }
    }

    @Override public void sendMessage(byte[] data) throws Exception
    {
        if (receiver == null || data.length == 0)
            return;

        int status = data[0] & 0xFF;

        // Reconstruct MidiMessage from raw bytes
        if (data.length <= 3)
        {
            int data1 = data.length > 1 ? data[1] & 0xFF : 0;
            int data2 = data.length > 2 ? data[2] & 0xFF : 0;
            ShortMessage msg = new ShortMessage();
            if (data.length == 1)
            {
                msg.setMessage(status);
            }
            else if (data.length == 2)
            {
                msg.setMessage(status, data1, 0);
            }
            else
            {
                msg.setMessage(status, data1, data2);
            }
            receiver.send(msg, -1);
        }
        else if (status == 0xF0)
        {
            SysexMessage msg = new SysexMessage();
            msg.setMessage(data, data.length);
            receiver.send(msg, -1);
        }
    }

    @Override public void closePort()
    {
        if (synth != null && synth.isOpen())
        {
            synth.close();
        }
    }
}
