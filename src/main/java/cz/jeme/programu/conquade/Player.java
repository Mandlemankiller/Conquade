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
import java.util.Map;

public enum Player {
    INSTANCE;

    private @Nullable StringBuilder frameBuilder;

    public void cleanup() {
        for (int i = 0; i < Conquade.TERMINAL_HEIGHT; i++) {
            System.out.println(AnsiHelper.CLEAR_LINE);
        }
        System.out.print(AnsiHelper.moveCursorUp(Conquade.TERMINAL_HEIGHT - 1));
        System.out.print(AnsiHelper.SHOW_CURSOR);
    }

    public void hookToShutdown() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.print(AnsiHelper.CLEAR_LINE + AnsiHelper.SHOW_CURSOR)));
    }

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
            clip = playAudio(audioFile);
            clip.start();
        } else if (args.doPlayAudio()) {
            Conquade.LOGGER.warning("The input file does not contain audio!");
        }

        try (final DataInputStream dis = new DataInputStream(new FileInputStream(videoFile))) {
            final int fps = dis.read();
            final int width = dis.readInt();
            final int height = dis.readInt();

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
                final byte[] data = dis.readNBytes(width * height * 2);
                if (data.length == 0) break;
                final String frame = readFrame(data, width, height);
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

    public void play(final @NotNull ConquadeArgs args) {
        play(new PlayArgs(args));
    }

    public @NotNull String readFrame(final byte @NotNull [] frameData, final int width, final int height) {
        if (frameBuilder == null)
            frameBuilder = new StringBuilder(width * height);
        frameBuilder.setLength(0);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int pixel = 2 * (y * width + x);
                final char content = Conquade.CHARACTERS_ARRAY[frameData[pixel]];
                int color = frameData[pixel + 1]; // hidden cast from byte to int, negative bytes stay negative!
                if (color < 0) color += 256; // negative bytes need to be moved to positive
                frameBuilder.append(AnsiChar.literal(content, color));
            }
            frameBuilder.append('\n');
        }
        frameBuilder.setLength(frameBuilder.length() - 1);
        return frameBuilder.toString();
    }

    public void printFrame(final @NotNull String frame, final int height) {
        System.out.print('\r');
        System.out.print(frame);
        System.out.print(AnsiHelper.moveCursorUp(height - 1));
    }

    public @NotNull Clip playAudio(final @NotNull File audioFile) {
        try {
            final Clip clip = AudioSystem.getClip();
            clip.open(AudioSystem.getAudioInputStream(audioFile));
            return clip;
        } catch (LineUnavailableException | UnsupportedAudioFileException | IOException e) {
            throw new IllegalStateException("Could not create audio clip!", e);
        }
    }

    public static final class PlayArgs {
        private final @NotNull File inputFile;
        private boolean ignoreResolution = false;
        public boolean playAudio = true;

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

        }


        public @NotNull File getInputFile() {
            return inputFile;
        }

        public boolean doIgnoreResolution() {
            return ignoreResolution;
        }

        public boolean doPlayAudio() {
            return playAudio;
        }
    }
}
