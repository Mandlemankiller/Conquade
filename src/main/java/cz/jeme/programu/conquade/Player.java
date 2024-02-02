package cz.jeme.programu.conquade;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

/**
 * A singleton class used to play Conquade video files.
 */
public enum Player {
    /**
     * The one and only {@link Player}.
     */
    INSTANCE;

    private @Nullable StringBuilder frameBuilder;

    /**
     * Cleanup the console after finishing a video.
     */
    public void cleanup() {
        for (int i = 0; i < Conquade.TERMINAL_HEIGHT; i++) {
            System.out.println(AnsiHelper.CLEAR_LINE);
        }
        System.out.print(AnsiHelper.moveCursorUp(Conquade.TERMINAL_HEIGHT - 1));
        System.out.print(AnsiHelper.SHOW_CURSOR);
    }

    /**
     * Create a shutdown hook ({@link Runtime#addShutdownHook(Thread)}) to clear some mess when the program is quit.
     */
    public void hookToShutdown() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.print(AnsiHelper.CLEAR_LINE + AnsiHelper.SHOW_CURSOR)));
    }

    /**
     * Plays the video with options from the args provided.
     *
     * @param args the play args
     */
    public void play(final @NotNull PlayArgs args) {
        final File inputFile = args.getInputFile();

        final File playerTmpDir = Conquade.prepareTmp().get(Conquade.TmpSubdir.PLAY);

        Conquade.LOGGER.info("Unpacking video and audio...");

        final File videoFile;
        File audioFile = null;
        final boolean hasAudio;
        try {
            final TarArchiveInputStream tarStream = new TarArchiveInputStream(new FileInputStream(inputFile));
            // video entry
            final TarArchiveEntry videoEntry = tarStream.getNextTarEntry();
            videoFile = Path.of(playerTmpDir.getAbsolutePath(), videoEntry.getName()).toFile();
            try {
                boolean ignored = videoFile.createNewFile();
            } catch (IOException e) {
                throw new IllegalStateException("Could not create video data file (\"%s\")!".formatted(videoFile.getAbsolutePath()), e);
            }
            IOUtils.copy(tarStream, new FileOutputStream(videoFile));
            // audio entry
            final TarArchiveEntry audioEntry = tarStream.getNextTarEntry();
            hasAudio = audioEntry != null && args.doPlayAudio();
            if (hasAudio) {
                audioFile = Path.of(playerTmpDir.getAbsolutePath(), audioEntry.getName()).toFile();
                try {
                    boolean ignored = audioFile.createNewFile();
                } catch (IOException e) {
                    throw new IllegalStateException("Could not create audio file (\"%s\")!".formatted(audioFile.getAbsolutePath()), e);
                }
                IOUtils.copy(tarStream, new FileOutputStream(audioFile));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not unpack input file (\"%s\")!"
                    .formatted(inputFile.getAbsolutePath()), e);
        }
        Conquade.LOGGER.info("Video and audio unpacked.");

        Clip clip = null;
        if (hasAudio) {
            clip = clipAudio(audioFile);
            clip.start();
        } else if (args.doPlayAudio()) {
            Conquade.LOGGER.warning("The input file does not contain audio!");
        }

        try (final DataInputStream dis = new DataInputStream(new FileInputStream(videoFile))) {
            final int fps = dis.read();
            final int width = dis.readInt();
            final int height = dis.readInt();
            final boolean trueColor = dis.readBoolean();

            if (trueColor && !Conquade.trueColor)
                throw new IllegalArgumentException("The video is rendered for a true color terminal, but -256 argument was used!");

            if (!args.doIgnoreResolution() && (width != Conquade.TERMINAL_WIDTH || height != Conquade.TERMINAL_HEIGHT))
                throw new IllegalArgumentException(("The video is rendered for terminal size %d×%d " +
                        "but the current terminal size is %d×%d!").formatted(
                        width,
                        height,
                        Conquade.TERMINAL_WIDTH,
                        Conquade.TERMINAL_HEIGHT
                ));

            hookToShutdown();
            Conquade.LOGGER.info("Playing the video...");
            Conquade.disableLogger();
            System.out.println(AnsiHelper.HIDE_CURSOR);

            while (true) {
                final long startTimeStamp = System.currentTimeMillis();
                final byte[] data = trueColor
                        ? dis.readNBytes(width * height * 4)
                        : dis.readNBytes(width * height * 2);
                if (data.length == 0) break;
                final String frame = trueColor
                        ? readFrameRGB(data, width, height, args.getColorTarget())
                        : readFrame256(data, width, height, args.getColorTarget());
                printFrame(frame, height);
                if (hasAudio) clip.start();
                long sleep = Math.round(1000D / fps) - (System.currentTimeMillis() - startTimeStamp);
                if (sleep < 0 && hasAudio) {
                    clip.stop();
                } else {
                    Thread.sleep(Math.max(0, sleep));
                }
            }
            cleanup();
            Conquade.enableLogger();
            Conquade.LOGGER.info("Video finished!");
        } catch (IOException e) {
            throw new IllegalStateException("Could not read video data file (\"%s\")!"
                    .formatted(videoFile.getAbsolutePath()), e);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Thread sleep interrupted!", e);
        }
    }

    /**
     * Transforms the {@link ConquadeArgs} to {@link PlayArgs} and plays the video.
     *
     * @param args the args to transform
     */
    public void play(final @NotNull ConquadeArgs args) {
        play(new PlayArgs(args));
    }

    /**
     * Transforms a 256 color video frame data to an ANSI 256 String representation of the frame.
     *
     * @param frameData   the frame data to transform
     * @param width       the width of the frame
     * @param height      the height of the frame
     * @param colorTarget what should be colored
     * @return ANSI String representation of the frame data
     */
    public @NotNull String readFrame256(final byte @NotNull [] frameData, final int width, final int height,
                                        final @NotNull Player.ColorTarget colorTarget) {
        if (frameBuilder == null)
            frameBuilder = new StringBuilder(width * height);
        frameBuilder.setLength(0);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int pixel = 2 * (y * width + x);
                final char content = Conquade.CHARACTERS_ARRAY[frameData[pixel]];
                int color = frameData[pixel + 1]; // hidden cast from byte to int, negative bytes stay negative!
                if (color < 0) color += 256; // negative bytes need to be moved to positive
                switch (colorTarget) {
                    case TEXT_ONLY -> frameBuilder.append(AnsiChar.literal256(content, color));
                    case HIGHLIGHT_ONLY -> frameBuilder.append(AnsiChar.literal256(' ', -1, color));
                    case BLACK_TEXT -> frameBuilder.append(AnsiChar.literal256(content, 0, color));
                }
            }
            frameBuilder.append('\n');
        }
        frameBuilder.setLength(frameBuilder.length() - 1);
        return frameBuilder.toString();
    }

    /**
     * Transforms an RGB color video frame data to an ANSI RGB String representation of the frame.
     *
     * @param frameData   the frame data to transform
     * @param width       the width of the frame
     * @param height      the height of the frame
     * @param colorTarget what should be colored
     * @return ANSI String representation of the frame data
     */
    public @NotNull String readFrameRGB(final byte @NotNull [] frameData, final int width, final int height,
                                        final @NotNull Player.ColorTarget colorTarget) {
        if (frameBuilder == null)
            frameBuilder = new StringBuilder(width * height);
        frameBuilder.setLength(0);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int pixel = 4 * (y * width + x);
                final char content = Conquade.CHARACTERS_ARRAY[frameData[pixel]];
                int red = frameData[pixel + 1];
                int green = frameData[pixel + 2];
                int blue = frameData[pixel + 3];
                // negative bytes need to be moved to positive
                if (red < 0) red += 256;
                if (green < 0) green += 256;
                if (blue < 0) blue += 256;
                switch (colorTarget) {
                    case TEXT_ONLY -> frameBuilder.append(AnsiChar.literalRGB(content, red, green, blue));
                    case HIGHLIGHT_ONLY -> frameBuilder.append(
                            AnsiChar.literalRGB(' ',
                                    -1, -1, -1,
                                    red, green, blue
                            ));
                    case BLACK_TEXT -> frameBuilder.append(
                            AnsiChar.literalRGB(content,
                                    0, 0, 0,
                                    red, green, blue
                            ));
                }
            }
            frameBuilder.append('\n');
        }
        frameBuilder.setLength(frameBuilder.length() - 1);
        return frameBuilder.toString();
    }


    /**
     * What part of the terminal should be colored.
     */
    public enum ColorTarget {
        /**
         * Color only text.
         */
        TEXT_ONLY,
        /**
         * Color only text highlight, do not print text.
         */
        HIGHLIGHT_ONLY,
        /**
         * Color only text highlight and print black text.
         */
        BLACK_TEXT;

        /**
         * Returns the lowercase enum constant name.
         *
         * @return {@link Enum#name()} but lowercase
         */
        @Override
        public @NotNull String toString() {
            return name().toLowerCase();
        }
    }

    /**
     * Prints a frame to the terminal.
     *
     * @param frame  the frame data String representation
     * @param height the terminal height
     */
    public void printFrame(final @NotNull String frame, final int height) {
        System.out.print('\r' + frame + AnsiHelper.moveCursorUp(height - 1));
    }

    /**
     * Returns an audio clip with the sound read from the provided file.
     *
     * @param audioFile the file to read sound from
     * @return the clip with opened sound
     * @throws IllegalStateException when the audio clip could not be created
     */
    public @NotNull Clip clipAudio(final @NotNull File audioFile) {
        try {
            final Clip clip = AudioSystem.getClip();
            clip.open(AudioSystem.getAudioInputStream(audioFile));
            return clip;
        } catch (LineUnavailableException | UnsupportedAudioFileException | IOException e) {
            throw new IllegalStateException("Could not create audio clip!", e);
        }
    }

    /**
     * {@link ConquadeArgs} wrapper for the {@link Player}.
     */
    public static final class PlayArgs {
        private final @NotNull File inputFile;
        private boolean ignoreResolution = false;
        private boolean playAudio = true;
        private @NotNull ColorTarget colorTarget = ColorTarget.TEXT_ONLY;

        /**
         * Read the args and construct a new {@link PlayArgs}, wrapping them.
         *
         * @param args the args to wrap
         * @throws IllegalArgumentException when any of the arguments is invalid or a required argument is missing
         */
        public PlayArgs(final @NotNull ConquadeArgs args) {
            Map<String, String> argMap = args.getArgMap();
            // Input file
            final String inputFilePath = argMap.get("i");
            if (inputFilePath == null)
                throw new IllegalArgumentException("Missing input file path argument (-i)!");
            inputFile = new File(inputFilePath);
            Conquade.validateInputFile(inputFile);
            if (!inputFile.getName().endsWith(Conquade.FILE_EXTENSION))
                throw new IllegalArgumentException("The input file is not a valid conquade file!");
            // Ignore resolution mismatch
            if (argMap.containsKey("nores")) {
                ignoreResolution = true;
                Conquade.LOGGER.fine("Resolution mismatches will be ignored (-nores).");
            }
            // Do not play audio
            if (argMap.containsKey("noaudio")) {
                playAudio = false;
                Conquade.LOGGER.fine("Audio will not be played (-noaudio).");
            }
            // Color target
            final String colorTargetStr = argMap.get("color");
            if (colorTargetStr != null) {
                try {
                    colorTarget = ColorTarget.valueOf(colorTargetStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Color argument is not valid! " +
                            "Valid color targets are: " + Arrays.toString(ColorTarget.values()), e);
                }
            }
        }


        /**
         * Returns the input Conquade video file.
         *
         * @return the Conquade video file
         */
        public @NotNull File getInputFile() {
            return inputFile;
        }

        /**
         * Returns whether to ignore resolution mismatch (when the terminal has a different size than the input Conquade video file).
         *
         * @return whether to ignore resolution mismatch
         */
        public boolean doIgnoreResolution() {
            return ignoreResolution;
        }

        /**
         * Returns whether to play audio.
         *
         * @return whether to play audio
         */
        public boolean doPlayAudio() {
            return playAudio;
        }

        /**
         * Returns what should be colored when printing out the frames (see {@link ColorTarget}).
         *
         * @return the color target
         */
        public @NotNull ColorTarget getColorTarget() {
            return colorTarget;
        }
    }
}
