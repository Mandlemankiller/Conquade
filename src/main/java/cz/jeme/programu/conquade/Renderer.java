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

/**
 * A singleton class used to render video files into Conquade video files.
 */
public enum Renderer {
    /**
     * The one and only {@link Renderer}.
     */
    INSTANCE;

    /**
     * Returns the number of .jpg frames in the provided directory.
     *
     * @param inputDir the directory to search frames in
     * @return the frame count
     */
    public int getFrameCount(final @NotNull File inputDir) {
        return Arrays.stream(Objects.requireNonNull(inputDir.list()))
                .filter(name -> name.endsWith(".jpg"))
                .mapToInt(name -> Integer.parseInt(name.substring(0, name.length() - 4))) // remove ".jpg"
                .max()
                .orElse(0);
    }

    /**
     * Renders the video with options from the args provided.
     *
     * @param args the render args
     */
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
            extractAudio(inputFile, audioFile, 0);
        extractFrames(inputFile, renderTmpDir, args.getFps(), Conquade.TERMINAL_WIDTH, Conquade.TERMINAL_HEIGHT);

        Conquade.LOGGER.info("Preparing to render frames...");


        try (final DataOutputStream dos = new DataOutputStream(new FileOutputStream(videoFile))) {
            // File header
            dos.write((byte) args.getFps());
            dos.writeInt(Conquade.TERMINAL_WIDTH);
            dos.writeInt(Conquade.TERMINAL_HEIGHT);
            dos.writeBoolean(Conquade.trueColor);

            // Prepare for render info
            System.out.printf("%s%n| LOADING |%n%n".formatted(
                    AnsiHelper.foregroundColor256(AnsiHelper.toAnsi256(0, 255, 255)) // aqua
            ));

            final int frameCount = getFrameCount(renderTmpDir);

            long renderTimeStamp = System.currentTimeMillis();
            long renderFrameId = 0;
            final StringBuilder etaBuilder = new StringBuilder();
            for (int frameId = 1; frameId < frameCount + 1; frameId++) {
                // Render
                final File frame = Path.of(renderTmpDir.getAbsolutePath(), frameId + ".jpg").toFile();
                if (Conquade.trueColor) {
                    dos.write(renderFrameRGB(frame));
                } else {
                    dos.write(renderFrame256(frame));
                }

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
                            ConquadeLogFormatter.DECIMAL_FORMATTER.format(percentage * 100),
                            frameId,
                            frameCount,
                            ConquadeLogFormatter.DECIMAL_FORMATTER.format(fps),
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

    /**
     * Transforms the {@link ConquadeArgs} to {@link RenderArgs} and renders the video.
     *
     * @param args the args to transform
     */
    public void render(final @NotNull ConquadeArgs args) {
        render(new RenderArgs(args));
    }

    /**
     * Transforms a {@link BufferedImage} into 256 color video frame data.
     *
     * @param frame the frame image to transform
     * @return 256 video frame data
     */
    public byte @NotNull [] renderFrame256(final @NotNull BufferedImage frame) {
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
                data[pixel + 1] = (byte) AnsiHelper.toAnsi256(red, green, blue);
            }
        }
        return data;
    }

    /**
     * Reads a {@link BufferedImage} from a file and transforms it into 256 color video frame data.
     *
     * @param image the frame image file to transform
     * @return 256 video frame data
     */
    public byte @NotNull [] renderFrame256(final @NotNull File image) {
        try {
            return renderFrame256(ImageIO.read(image));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read image file (\"%s\")!".formatted(image.getAbsolutePath()), e);
        }
    }

    /**
     * Transforms a {@link BufferedImage} into RGB color video frame data.
     *
     * @param frame the frame image to transform
     * @return RGB video frame data
     */
    public byte @NotNull [] renderFrameRGB(final @NotNull BufferedImage frame) {
        final int width = frame.getWidth();
        final int height = frame.getHeight();
        byte[] data = new byte[width * height * 4]; // each pixel takes 4 bytes - character index, red, green and blue

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int rgb = frame.getRGB(x, y);
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;

                double luma = red * 0.299D + green * 0.587D + blue * 0.114D; // 0 - 255
                int index = Math.max(0, Math.min((int) Math.round(luma), Conquade.CHARACTERS.length() - 1));

                int pixel = 4 * (y * width + x); // each pixel takes 4 bytes
                data[pixel] = (byte) index;
                data[pixel + 1] = (byte) red;
                data[pixel + 2] = (byte) green;
                data[pixel + 3] = (byte) blue;
            }
        }
        return data;
    }

    /**
     * Reads a {@link BufferedImage} from a file and transforms it into RGB color video frame data.
     *
     * @param image the frame image file to transform
     * @return RGB video frame data
     */
    public byte @NotNull [] renderFrameRGB(final @NotNull File image) {
        try {
            return renderFrameRGB(ImageIO.read(image));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read image file (\"%s\")!".formatted(image.getAbsolutePath()), e);
        }
    }

    /**
     * Extracts an audio track from a file.
     *
     * @param inputFile  the input file to extract audio from
     * @param outputFile the output audio file
     * @param audioTrack the audio track to extract
     */
    public void extractAudio(final @NotNull File inputFile, final @NotNull File outputFile, final int audioTrack) {
        Conquade.LOGGER.info("Extracting audio using ffmpeg...");
        final String cmd = "%s -y -i %s -map 0:a:%d -loglevel warning %s"
                .formatted(
                        Conquade.ffmpegExe,
                        inputFile.getAbsolutePath(),
                        audioTrack,
                        outputFile.getAbsolutePath()
                );
        Conquade.exec(cmd);
        Conquade.LOGGER.info("Audio extracted (\"%s\").".formatted(outputFile));
    }

    /**
     * Extracts frames from a video.
     *
     * @param inputFile    the input file to extract frames from
     * @param outputFolder the output folder to extract frames to
     * @param fps          how many frames should be extracted per video second
     * @param width        the width of the frames
     * @param height       the height of the frames
     */
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

    /**
     * {@link ConquadeArgs} wrapper for the {@link Renderer}.
     */
    public static final class RenderArgs {
        private final @NotNull File inputFile;
        private final @NotNull File outputFile;
        private boolean overwriteOutput = false;
        private boolean renderAudio = true;
        private int fps = 30;

        /**
         * Read the args and construct a new {@link RenderArgs}, wrapping them.
         *
         * @param args the args to wrap
         * @throws IllegalArgumentException when any of the arguments is invalid or a required argument is missing
         */
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

        /**
         * Returns the input video file.
         *
         * @return the video file
         */
        public @NotNull File getInputFile() {
            return inputFile;
        }

        /**
         * Returns the output Conquade video file.
         *
         * @return the Conquade video file
         */
        public @NotNull File getOutputFile() {
            return outputFile;
        }

        /**
         * Returns whether to overwrite the output file if it exists.
         *
         * @return whether to overwrite the output
         */
        public boolean doOverwriteOutput() {
            return overwriteOutput;
        }

        /**
         * Returns the output video framerate.
         *
         * @return the output video fps
         */
        public int getFps() {
            return fps;
        }

        /**
         * Returns whether to render audio or not.
         *
         * @return whether to render audio
         */
        public boolean doRenderAudio() {
            return renderAudio;
        }
    }
}
