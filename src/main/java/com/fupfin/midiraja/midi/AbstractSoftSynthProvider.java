/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.fupfin.midiraja.midi;

import com.fupfin.midiraja.dsp.AudioProcessor;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.jspecify.annotations.Nullable;

/**
 * Base class for synthesizers that use a native bridge and a dedicated render thread.
 *
 * @param <T> The type of the native bridge.
 */
@SuppressWarnings({"ThreadPriorityCheck", "EmptyCatch"})
public abstract class AbstractSoftSynthProvider<T extends MidiNativeBridge>
        implements SoftSynthProvider
{
    protected final T bridge;
    protected final @Nullable AudioProcessor audioOut;
    protected final ConcurrentLinkedQueue<byte[]> eventQueue = new ConcurrentLinkedQueue<>();

    protected @Nullable Thread renderThread;
    protected volatile boolean running = false;
    protected volatile boolean renderPaused = false;

    protected static final int SAMPLE_RATE = 44100;
    protected static final int FRAMES_PER_RENDER = 512;

    protected AbstractSoftSynthProvider(T bridge, @Nullable AudioProcessor audioOut)
    {
        this.bridge = bridge;
        this.audioOut = audioOut;
    }

    @Override
    public void sendMessage(byte[] data)
    {
        if (data == null || data.length == 0) return;
        eventQueue.offer(data.clone());
    }

    @Override
    public void panic()
    {
        eventQueue.clear();
        for (int ch = 0; ch < 16; ch++)
        {
            try
            {
                eventQueue.offer(new byte[] {(byte) (0xB0 | ch), 64, 0});
                eventQueue.offer(new byte[] {(byte) (0xB0 | ch), 123, 0});
                eventQueue.offer(new byte[] {(byte) (0xB0 | ch), 120, 0});
                eventQueue.offer(new byte[] {(byte) (0xB0 | ch), 121, 0});
            }
            catch (Exception ignored)
            {
            }
        }
        if (audioOut != null)
        {
            audioOut.reset();
            renderPaused = true;
        }
    }

    @Override
    public void prepareForNewTrack(javax.sound.midi.Sequence sequence)
    {
        renderPaused = true;
        try
        {
            Thread.sleep(20);
        }
        catch (InterruptedException ignored)
        {
        }

        eventQueue.clear();
        bridge.panic();

        if (audioOut != null)
        {
            audioOut.reset();
        }

        bridge.reset();
    }

    @Override
    public void onPlaybackStarted()
    {
        renderPaused = false;
    }

    @Override
    public void closePort()
    {
        running = false;
        if (renderThread != null)
        {
            renderThread.interrupt();
            try
            {
                renderThread.join(500);
            }
            catch (InterruptedException ignored)
            {
            }
        }
        bridge.close();
    }

    protected void startRenderThread(String name)
    {
        running = true;
        renderThread = new Thread(() -> {
            short[] pcmBuffer = new short[FRAMES_PER_RENDER * 2];
            while (running)
            {
                if (renderPaused)
                {
                    try
                    {
                        Thread.sleep(1);
                    }
                    catch (InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }

                byte[] event;
                while ((event = eventQueue.poll()) != null)
                {
                    dispatchToNative(event);
                }

                bridge.generate(pcmBuffer, FRAMES_PER_RENDER);

                if (audioOut != null)
                {
                    audioOut.processInterleaved(pcmBuffer, FRAMES_PER_RENDER, 2);
                }
                else
                {
                    try
                    {
                        Thread.sleep(10);
                    }
                    catch (InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, name);
        renderThread.setPriority(Thread.MAX_PRIORITY);
        renderThread.setDaemon(true);
        renderThread.start();
    }

    protected void dispatchToNative(byte[] data)
    {
        if(data==null||data.length==0)return;int status=data[0]&0xFF;if(status>=0xF0){if(data.length>1)bridge.systemExclusive(data);return;}

        int command=status&0xF0;int channel=status&0x0F;if(data.length<2)return;int data1=data[1]&0xFF;int data2=(data.length>=3)?(data[2]&0xFF):0;

        switch(command){case 0x90->bridge.noteOn(channel,data1,data2);case 0x80->bridge.noteOff(channel,data1);case 0xB0->bridge.controlChange(channel,data1,data2);case 0xC0->bridge.patchChange(channel,data1);case 0xE0->bridge.pitchBend(channel,(data2<<7)|data1);}
    }

    /**
     * Test-only: drains the event queue and dispatches all pending events to the bridge. In
     * production, the render thread does this automatically before each generate() call.
     */
    public void flushEventQueueForTest()
    {
        byte[] event;
        while ((event = eventQueue.poll()) != null)
        {
            dispatchToNative(event);
        }
    }
}
