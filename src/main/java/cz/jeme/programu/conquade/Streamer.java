package cz.jeme.programu.conquade;

import org.jetbrains.annotations.NotNull;

import javax.sound.sampled.Clip;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

/**
 * A singleton class used to stream video files (play without prerendering them).
 */
public enum Streamer {
    /**
     * The one and only {@link Streamer}.
     */
    INSTANCE;

    /**
     * Time in milliseconds between starting the ffmpeg renderer thread and playing the video.
     */
    public static final long FFMPEG_ADVANCE = 1000;

    /**
     * Streams the video with options from the args provided.
     *
     * @param args the stream args
     */
    public void stream(final @NotNull StreamArgs args) {
        final File inputFile = args.getInputFile();
        final File renderTmpDir = Conquade.prepareTmp().get(Conquade.TmpSubdir.RENDER);

        final File audioFile = Path.of(renderTmpDir.getAbsolutePath(), "audio.wav").toFile();
        if (args.doStreamAudio())
            Renderer.INSTANCE.extractAudio(inputFile, audioFile, 0);
        Thread frameRenderThread = new Thread(() -> { // Async render frames with ffmpeg
            Renderer.INSTANCE.extractFrames(
                    inputFile,
                    renderTmpDir,
                    args.getFps(),
                    Conquade.TERMINAL_WIDTH,
                    Conquade.TERMINAL_HEIGHT
            );
        });
        frameRenderThread.start();

        try {
            Thread.sleep(FFMPEG_ADVANCE);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Could not wait after issuing ffmpeg render!");
        }

        Clip clip = null;
        if (args.doStreamAudio()) {
            clip = Player.INSTANCE.clipAudio(audioFile);
            clip.start();
        }

        Player.INSTANCE.hookToShutdown();
        Conquade.LOGGER.info("Streaming the video...");
        Conquade.disableLogger();
        System.out.println(AnsiHelper.HIDE_CURSOR);

        int frameId = 1;
        while (true) {
            final long startTimeStamp = System.currentTimeMillis();
            final File frame = Path.of(renderTmpDir.getAbsolutePath(), frameId + ".jpg").toFile();
            if (!frame.exists()) {
                if (frameRenderThread.isAlive()) {
                    throw new IllegalStateException("Outrun ffmpeg frame renderer while streaming!");
                } else {
                    break;
                }
            }
            final String frameStr;
            if (Conquade.trueColor) {
                final byte[] renderedFrame = Renderer.INSTANCE.renderFrameRGB(frame);
                frameStr = Player.INSTANCE.readFrameRGB(renderedFrame, Conquade.TERMINAL_WIDTH, Conquade.TERMINAL_HEIGHT, args.getColorTarget());
            } else {
                final byte[] renderedFrame = Renderer.INSTANCE.renderFrame256(frame);
                frameStr = Player.INSTANCE.readFrame256(renderedFrame, Conquade.TERMINAL_WIDTH, Conquade.TERMINAL_HEIGHT, args.getColorTarget());
            }

            Player.INSTANCE.printFrame(frameStr, Conquade.TERMINAL_HEIGHT);
            if (!frame.delete())
                throw new IllegalStateException("Could not delete frame file (\"%s\")!".formatted(frame.getAbsolutePath()));
            if (clip != null) clip.start();
            long sleep = Math.round(1000D / args.getFps()) - (System.currentTimeMillis() - startTimeStamp);
            if (sleep < 0 && clip != null) {
                clip.stop();
            } else {
                try {
                    Thread.sleep(Math.max(0, sleep));
                } catch (InterruptedException e) {
                    throw new IllegalStateException("Thread sleep interrupted!", e);
                }
            }
            frameId++;
        }
        Player.INSTANCE.cleanup();
        Conquade.enableLogger();
        Conquade.LOGGER.info("Video finished!");
    }

    /**
     * Transforms the {@link ConquadeArgs} to {@link StreamArgs} and renders the video.
     *
     * @param args the args to transform
     */
    public void stream(final @NotNull ConquadeArgs args) {
        stream(new StreamArgs(args));
    }

    /**
     * {@link ConquadeArgs} wrapper for the {@link Streamer}.
     */
    public static final class StreamArgs {
        private final @NotNull File inputFile;
        private boolean streamAudio = true;
        private int fps = 25;
        private @NotNull Player.ColorTarget colorTarget = Player.ColorTarget.TEXT_ONLY;

        /**
         * Read the args and construct a new {@link StreamArgs}, wrapping them.
         *
         * @param args the args to wrap
         * @throws IllegalArgumentException when any of the arguments is invalid or a required argument is missing
         */
        public StreamArgs(final @NotNull ConquadeArgs args) {
            Map<String, String> argMap = args.getArgMap();
            // Input file
            final String inputFilePath = argMap.get("i");
            if (inputFilePath == null)
                throw new IllegalArgumentException("Missing input file path argument (-i)!");
            inputFile = new File(inputFilePath);
            Conquade.validateInputFile(inputFile);
            // FPS
            String fpsStr = argMap.get("fps");
            if (fpsStr == null) {
                Conquade.LOGGER.fine("FPS defaulted to %d.".formatted(fps));
            } else {
                try {
                    fps = Integer.parseInt(fpsStr);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("FPS argument value is not a valid number!", e);
                }
                if (fps < 1 || fps > 255)
                    throw new IllegalArgumentException("FPS argument value must be a number between 1 and 255!");
                Conquade.LOGGER.fine("FPS set to %d.".formatted(fps));
            }
            // Audio
            if (argMap.containsKey("noaudio")) {
                streamAudio = false;
                Conquade.LOGGER.fine("Audio will not be streamed (-noaudio).");
            }
            // Color target
            final String colorTargetStr = argMap.get("color");
            if (colorTargetStr != null) {
                try {
                    colorTarget = Player.ColorTarget.valueOf(colorTargetStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Color argument is not valid! " +
                            "Valid color targets are: " + Arrays.toString(Player.ColorTarget.values()), e);
                }
            }
        }

        /**
         * Returns the input video file.
         *
         * @return the video file
         */
        public @NotNull File getInputFile() {
            return inputFile;
        }

        /**
         * Returns the streamed video framerate.
         *
         * @return the streamed video fps
         */
        public int getFps() {
            return fps;
        }

        /**
         * Returns whether to stream audio or not.
         *
         * @return whether to stream audio
         */
        public boolean doStreamAudio() {
            return streamAudio;
        }

        /**
         * Returns what should be colored when printing out the frames (see {@link Player.ColorTarget}).
         *
         * @return the color target
         */
        public @NotNull Player.ColorTarget getColorTarget() {
            return colorTarget;
        }
    }
}
