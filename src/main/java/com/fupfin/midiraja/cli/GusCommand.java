/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.fupfin.midiraja.cli;

import com.fupfin.midiraja.MidirajaCommand;
import com.fupfin.midiraja.midi.NativeAudioEngine;
import com.fupfin.midiraja.midi.gus.GusSynthProvider;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "gus", mixinStandardHelpOptions = true,
        description = "Play using Pure Java Gravis Ultrasound (GUS) Synthesizer")
public class GusCommand implements Callable<Integer>
{
    @ParentCommand
    @org.jspecify.annotations.Nullable
    private MidirajaCommand parent;

    @Mixin
    private FxOptions fxOptions = new FxOptions();

    @Mixin
    private CommonOptions common = new CommonOptions();

    @Option(names = {"-p", "--patch-dir"},
            description = "Directory containing GUS .pat files and gus.cfg (or timidity.cfg)")
    private Optional<File> patchDir = Optional.empty();



    @Parameters(paramLabel = "<file>", description = "MIDI files or M3U playlists to play")
    private List<File> files = new java.util.ArrayList<>();

    @Override
    public Integer call() throws Exception
    {
        var p = java.util.Objects.requireNonNull(parent);
        String audioLib = AudioLibResolver.resolve();
        NativeAudioEngine audio = new NativeAudioEngine(audioLib);
        audio.init(44100, 2, 4096);
        if (common != null && common.dumpWav.isPresent())
        {
            audio.enableDump(common.dumpWav.get());
        }

        com.fupfin.midiraja.dsp.AudioProcessor pipeline =
                new com.fupfin.midiraja.dsp.FloatToShortSink(audio);
        pipeline = common.wrapRetroPipeline(pipeline);
        pipeline = java.util.Objects.requireNonNull(fxOptions).wrapFxPipeline(pipeline);
        if (java.util.Objects.requireNonNull(fxOptions).needsFloatConversion(common))
        {
            pipeline = new com.fupfin.midiraja.dsp.ShortToFloatFilter(pipeline);
        }

        String dirPath = patchDir.map(File::getAbsolutePath).orElse(null);

        var provider = new GusSynthProvider(pipeline, dirPath);

        var runner =
                new PlaybackRunner(p.getOut(), p.getErr(), p.getTerminalIO(), p.isInTestMode());
        return runner.run(provider, true, Optional.empty(), patchDir.map(File::getPath), files,
                java.util.Objects.requireNonNull(common));
    }
}
