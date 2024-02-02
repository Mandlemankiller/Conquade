package cz.jeme.programu.conquade;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public enum Renderer {
    INSTANCE;

    public int getFrameCount(final @NotNull File inputDir) {
        return Arrays.stream(Objects.requireNonNull(inputDir.list()))
                .filter(name -> name.endsWith(".jpg"))
                .mapToInt(name -> Integer.parseInt(name.substring(0, name.length() - 4))) // remove ".jpg"
                .max()
                .orElse(0);
    }

    public void render(final @NotNull RenderArgs args) {
        final File inputFile = args.getInputFile();

        final File renderTmpDir = Conquade.prepareTmp().get(Conquade.TmpSubdir.RENDER);

        // Create video data file
        final File videoFile = Path.of(renderTmpDir.getAbsolutePath(), "video.dat").toFile();
        try {
            boolean ignored = videoFile.createNewFile();
        } catch (IOException e) {
            throw new IllegalStateException("Could not create video data file (\"%s\")!".formatted(videoFile.getAbsolutePath()), e);
        }

        final File audioFile = Path.of(renderTmpDir.getAbsolutePath(), "audio.wav").toFile();

        if (args.doRenderAudio())
            extractAudio(inputFile, audioFile);
        extractFrames(inputFile, renderTmpDir, args.getFps(), Conquade.TERMINAL_WIDTH, Conquade.TERMINAL_HEIGHT);

        Conquade.LOGGER.info("Preparing to render frames...");


        try (final DataOutputStream dos = new DataOutputStream(new FileOutputStream(videoFile))) {
            // File header
            dos.write((byte) args.getFps());
            dos.writeInt(Conquade.TERMINAL_WIDTH);
            dos.writeInt(Conquade.TERMINAL_HEIGHT);

            // Prepare for render info
            System.out.printf("%s%n| LOADING |%n%n".formatted(
                    AnsiHelper.foregroundColor(AnsiHelper.toAnsi(0, 255, 255)) // aqua
            ));

            final int frameCount = getFrameCount(renderTmpDir);

            long renderTimeStamp = System.currentTimeMillis();
            long renderFrameId = 0;
            final StringBuilder etaBuilder = new StringBuilder();
            for (int frameId = 1; frameId < frameCount + 1; frameId++) {
                // Render
                final File frame = Path.of(renderTmpDir.getAbsolutePath(), frameId + ".jpg").toFile();
                dos.write(renderFrame(frame));

                // Render info
                final long currentTimeStamp = System.currentTimeMillis();
                if (currentTimeStamp - renderTimeStamp >= 1000 || frameId == frameCount) {
                    double percentage = frameId / (double) frameCount;
                    double fps = (frameId - renderFrameId) * ((currentTimeStamp - renderTimeStamp) / 1000D);

                    // eta
                    etaBuilder.setLength(0); // clear etaBuilder
                    int seconds = (int) Math.round((frameCount - frameId) / fps);
                    int hours = seconds / 3600;
                    if (hours > 0) {
                        etaBuilder.append(hours).append("hrs ");
                        seconds -= hours * 3600;
                    }
                    int minutes = seconds / 60;
                    if (hours > 0 || minutes > 0) {
                        etaBuilder.append(minutes).append("min ");
                        seconds -= minutes * 60;
                    }
                    etaBuilder.append(seconds).append("sec");

                    final String loadbar = "=".repeat(Math.max(0, (int) (Conquade.TERMINAL_WIDTH * percentage) - 1)) + ">";

                    System.out.print(AnsiHelper.moveCursorUp(3));
                    System.out.println(AnsiHelper.CLEAR_LINE + loadbar);
                    System.out.printf(AnsiHelper.CLEAR_LINE + "| RENDERING | %s%% | %d/%d frames | %s FPS | %s ETA |%n",
                            ConquadeFormatter.DECIMAL_FORMATTER.format(percentage * 100),
                            frameId,
                            frameCount,
                            ConquadeFormatter.DECIMAL_FORMATTER.format(fps),
                            etaBuilder
                    );
                    System.out.println(AnsiHelper.CLEAR_LINE + loadbar);

                    renderTimeStamp = currentTimeStamp;
                    renderFrameId = frameId;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not write to video data file (\"%s\")!"
                    .formatted(videoFile.getAbsolutePath()), e);
        }

        Conquade.LOGGER.info("Frames rendered (\"%s\").".formatted(videoFile.getAbsolutePath()));
        Conquade.LOGGER.info("Merging video with audio...");

        // Create output conquade file
        final File outputFile = args.getOutputFile();
        final boolean outputFileExists;
        try {
            outputFileExists = !outputFile.createNewFile();
        } catch (IOException e) {
            throw new IllegalStateException("Could not create output file (\"%s\")!".formatted(outputFile.getAbsolutePath()), e);
        }
        if (outputFileExists) {
            if (args.doOverwriteOutput()) {
                if (!outputFile.delete())  // File could not be deleted
                    throw new IllegalStateException("Could not delete (overwrite) output file (\"%s\")!".formatted(outputFile.getAbsolutePath()));
                try {
                    boolean ignored = outputFile.createNewFile();
                } catch (IOException e) {
                    throw new IllegalStateException("Could not create (overwrite) output file (\"%s\")!".formatted(outputFile.getAbsolutePath()), e);
                }
            } else
                throw new IllegalStateException("The output file already exists! If you want to overwrite it, use the -force argument.");
        }

        // Tar video and audio
        try {
            final TarArchiveOutputStream tarStream = new TarArchiveOutputStream(new FileOutputStream(outputFile.getAbsolutePath()));
            // tar video
            final TarArchiveEntry videoEntry = new TarArchiveEntry(videoFile, videoFile.getName());
            tarStream.putArchiveEntry(videoEntry);
            IOUtils.copy(new FileInputStream(videoFile), tarStream);
            tarStream.closeArchiveEntry();
            if (audioFile.exists()) { // tar audio
                final TarArchiveEntry audioEntry = new TarArchiveEntry(audioFile, audioFile.getName());
                tarStream.putArchiveEntry(audioEntry);
                IOUtils.copy(new FileInputStream(audioFile), tarStream);
                tarStream.closeArchiveEntry();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not tar video (\"%s\") and audio (\"%s\") to output file (\"%s\")!"
                    .formatted(videoFile.getAbsolutePath(), audioFile.getAbsolutePath(), outputFile.getAbsolutePath()), e);
        }

        Conquade.LOGGER.info("Video and audio merged.");
        Conquade.LOGGER.info("Done! The output file is located at \"%s\".".formatted(outputFile.getAbsolutePath()));
    }

    public void render(final @NotNull ConquadeArgs args) {
        render(new RenderArgs(args));
    }

    public byte @NotNull [] renderFrame(final @NotNull BufferedImage frame) {
        final int width = frame.getWidth();
        final int height = frame.getHeight();
        byte[] data = new byte[width * height * 2]; // each pixel takes 2 bytes - character index and ansi color

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int rgb = frame.getRGB(x, y);
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;

                double luma = red * 0.299D + green * 0.587D + blue * 0.114D; // 0 - 255
                int index = Math.max(0, Math.min((int) Math.round(luma), Conquade.CHARACTERS.length() - 1));

                int pixel = 2 * (y * width + x); // each pixel takes 2 bytes
                data[pixel] = (byte) index;
                data[pixel + 1] = (byte) AnsiHelper.toAnsi(red, green, blue);
            }
        }
        return data;
    }

    public byte @NotNull [] renderFrame(final @NotNull File image) {
        try {
            return renderFrame(ImageIO.read(image));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read image file (\"%s\")!".formatted(image.getAbsolutePath()), e);
        }
    }

    public void extractAudio(final @NotNull File inputFile, final @NotNull File outputFile) {
        Conquade.LOGGER.info("Extracting audio using ffmpeg...");
        final String cmd = "%s -y -i %s -map 0:a:0 -loglevel warning %s"
                .formatted(
                        Conquade.ffmpegExe,
                        inputFile.getAbsolutePath(),
                        outputFile.getAbsolutePath()
                );
        Conquade.exec(cmd);
        Conquade.LOGGER.info("Audio extracted (\"%s\").".formatted(outputFile));
    }

    public void extractFrames(final @NotNull File inputFile, final @NotNull File outputFolder, final int fps, final int width, final int height) {
        Conquade.LOGGER.info("Extracting frames using ffmpeg...");
        final String cmd = "%s -y -i %s -r %d -an -vf scale=%d:%d -pix_fmt yuvj420p -q:v 1 %s%%d.jpg"
                .formatted(
                        Conquade.ffmpegExe,
                        inputFile.getAbsolutePath(),
                        fps,
                        width,
                        height,
                        outputFolder.getAbsolutePath() + File.separator
                );
        Conquade.exec(cmd);
        Conquade.LOGGER.info("Frames extracted (\"%s*.jpg\").".formatted(outputFolder.getAbsolutePath() + File.separator));
    }


    public static final class RenderArgs {
        private final @NotNull File inputFile;
        private final @NotNull File outputFile;
        private boolean overwriteOutput = false;
        private boolean renderAudio = true;
        private int fps = 30;

        public RenderArgs(final @NotNull ConquadeArgs args) {
            Map<String, String> argMap = args.getArgMap();
            // Input file
            final String inputFilePath = argMap.get("i");
            if (inputFilePath == null)
                throw new IllegalArgumentException("Missing input file path argument (-i)!");
            inputFile = new File(inputFilePath);
            Conquade.validateInputFile(inputFile);
            // Output file
            String outputFilePath = argMap.get("o");
            if (outputFilePath == null)
                throw new IllegalArgumentException("Missing output file path argument (-o)!");
            if (!outputFilePath.endsWith(Conquade.FILE_EXTENSION)) {
                outputFilePath += Conquade.FILE_EXTENSION;
                Conquade.LOGGER.warning("Output file path changed to \"%s\" (missing extension).".formatted(new File(outputFilePath).getAbsolutePath()));
            }
            outputFile = new File(outputFilePath);
            // Overwrite output
            if (argMap.containsKey("force")) {
                overwriteOutput = true;
                Conquade.LOGGER.fine("Output file will be overwritten (-force).");
            }
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
                renderAudio = false;
                Conquade.LOGGER.fine("Audio will not be rendered (-noaudio).");
            }
        }

        public @NotNull File getInputFile() {
            return inputFile;
        }

        public @NotNull File getOutputFile() {
            return outputFile;
        }

        public boolean doOverwriteOutput() {
            return overwriteOutput;
        }

        public int getFps() {
            return fps;
        }

        public boolean doRenderAudio() {
            return renderAudio;
        }
    }
}
