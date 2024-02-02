package cz.jeme.programu.conquade;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.*;
import java.util.stream.Collectors;

/**
 * The main class of Conquade.
 */
public final class Conquade {
    /**
     * The characters used for rendering and playing the video. All the characters must be in ASCII.
     * <p>Max length is 255 characters!</p>
     */
    public static final @NotNull String CHARACTERS = " ._-:!?71iIca234db56O089$W%#@Ñ";
    /**
     * A char array representing the rendering {@link Conquade#CHARACTERS}.
     */
    public static final char @NotNull [] CHARACTERS_ARRAY = CHARACTERS.toCharArray();
    /**
     * The Conquade file extension.
     */
    public static final @NotNull String FILE_EXTENSION = ".cqd";
    /**
     * The Conquade logger.
     */
    public static final @NotNull Logger LOGGER = Logger.getLogger(Conquade.class.getName());
    /**
     * The width of the terminal obtained when the script started.
     */
    public static final int TERMINAL_WIDTH;
    /**
     * The height of the terminal obtained when the script started.
     */
    public static final int TERMINAL_HEIGHT;
    /**
     * The temporary Conquade directory.
     */
    public static @NotNull File conquadeTmpDir;
    /**
     * All the {@link Process}es run by Conquade.
     */
    private static final @NotNull Set<Process> PROCESSES = new HashSet<>();

    /**
     * Whether the debug mode is enabled.
     */
    public static boolean debug = false;

    /**
     * Whether the terminal supports truecolor.
     */
    public static boolean trueColor = true;

    static {
        try {
            final Terminal terminal = TerminalBuilder.terminal();
            TERMINAL_WIDTH = terminal.getWidth();
            TERMINAL_HEIGHT = terminal.getHeight() - 1; // reserved for screen overflow
        } catch (IOException e) {
            throw new IllegalStateException("Could not detect terminal size!", e);
        }

        final String systemTmpDirPath = System.getProperty("java.io.tmpdir");
        if (systemTmpDirPath == null)
            throw new IllegalStateException("Could not detect system tmp directory!");
        conquadeTmpDir = Path.of(systemTmpDirPath, "conquade").toFile();

        if (CHARACTERS.length() > 255) // juts in case I had the amazing idea to change the constant to some bullshit
            throw new IllegalStateException("The maximum number of characters is 255!");
    }

    /**
     * The ffmpeg executable used to extract frames and audio.
     */
    public static @NotNull String ffmpegExe = "ffmpeg";

    /**
     * Enable terminal logging.
     */
    public static void enableLogger() {
        LOGGER.setLevel(debug ? Level.FINE : Level.INFO);
    }

    /**
     * Disable terminal logging.
     */
    public static void disableLogger() {
        LOGGER.setLevel(Level.OFF);
    }

    public static void main(final String @NotNull [] args) {
        LogManager.getLogManager().reset();
        final Handler logHandler = new ConsoleHandler();
        logHandler.setLevel(Level.FINEST);
        logHandler.setFormatter(ConquadeLogFormatter.getInstance());
        LOGGER.addHandler(logHandler);
        try { // logging logic
            main0(args);
        } catch (Throwable t) {
            logError("An error occurred in \"main\"!", t);
            PROCESSES.forEach(Process::destroy);
            System.exit(1);
        }
    }

    private static void main0(final String @NotNull [] args) {
        final ConquadeArgs conquadeArgs = new ConquadeArgs(args);

        if (conquadeArgs.getArgMap().containsKey("256"))
            trueColor = false;


        if (conquadeArgs.getArgMap().containsKey("debug")) {
            debug = true;
            enableLogger(); // only sets the logging level
            LOGGER.fine("Debug mode enabled.");
        }

        if (trueColor) {
            LOGGER.fine("Using true color mode (16,777,216 colors).");
        } else {
            LOGGER.fine("Using 256 color mode.");
        }

        LOGGER.fine("Detected terminal size %d×%d.".formatted(TERMINAL_WIDTH, TERMINAL_HEIGHT));

        final String newConquadeTmpDir = conquadeArgs.getArgMap().get("tmp");
        if (newConquadeTmpDir == null) {
            LOGGER.fine("Conquade tmp directory defaulted to \"%s\".".formatted(conquadeTmpDir.getAbsolutePath()));
        } else {
            conquadeTmpDir = new File(newConquadeTmpDir);
            LOGGER.fine("Conquade tmp directory set to \"%s\".".formatted(conquadeTmpDir.getAbsolutePath()));
        }
        final String newFfmpegExe = conquadeArgs.getArgMap().get("ffmpeg");
        if (newFfmpegExe == null) {
            LOGGER.fine("Ffmpeg executable defaulted to \"%s\".".formatted(ffmpegExe));
        } else {
            ffmpegExe = newFfmpegExe;
            LOGGER.fine("Ffmpeg executable set to \"%s\".".formatted(ffmpegExe));
        }

        switch (conquadeArgs.getAction()) {
            case RENDER -> Renderer.INSTANCE.render(conquadeArgs);
            case PLAY -> Player.INSTANCE.play(conquadeArgs);
            case STREAM -> Streamer.INSTANCE.stream(conquadeArgs);
            case HELP -> help();
        }
    }

    /**
     * Print usage info.
     *
     * @throws IllegalStateException when the usage.txt file does not exist or it could not be read
     */
    public static void help() {
        LOGGER.fine("Printing usage info...");
        try {
            final InputStream usageStream = Conquade.class.getClassLoader().getResourceAsStream("usage.txt");
            if (usageStream == null)
                throw new IllegalStateException("Could not find usage.txt!");
            final BufferedReader usageReader = new BufferedReader(new InputStreamReader(usageStream, StandardCharsets.UTF_8));
            String line;
            while ((line = usageReader.readLine()) != null)
                System.out.println(line);

            usageReader.close();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read usage.txt!", e);
        }
    }

    /**
     * Log a throwable with stack trace.
     *
     * @param level     the log entry level
     * @param message   the log message
     * @param throwable the throwable to log
     */
    public static void logError(final @NotNull Level level, final @NotNull String message, final @NotNull Throwable throwable) {
        final StringWriter stringWriter = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(stringWriter);
        throwable.printStackTrace(printWriter);
        String stackTraceStr = stringWriter.toString();
        stackTraceStr = stackTraceStr.substring(0, stackTraceStr.length() - 1); // Remove last newline
        LOGGER.log(level, message + '\n' + stackTraceStr);
    }

    /**
     * Log a throwable with {@link Level#SEVERE}.
     *
     * @param message   the log message
     * @param throwable the throwable to log
     */
    public static void logError(final @NotNull String message, final @NotNull Throwable throwable) {
        logError(Level.SEVERE, message, throwable);
    }

    /**
     * Log a throwable with {@link Level#SEVERE} and no message.
     *
     * @param throwable the throwable to log
     */
    public static void logError(final @NotNull Throwable throwable) {
        logError("", throwable);
    }

    /**
     * Validate whether the input file exists.
     *
     * @param inputFile the input file to validate
     * @throws IllegalArgumentException when the input file does not exist
     */
    public static void validateInputFile(final @NotNull File inputFile) {
        if (!inputFile.exists())
            throw new IllegalArgumentException("Input file (\"%s\") does not exist!".formatted(inputFile.getAbsolutePath()));
    }

    /**
     * Deletes the Conquade temporary directory if it exists, recreates it and creates all the subdirectories (see {@link TmpSubdir}).
     *
     * @return a map containing all the Conquade tmp subdirectories specified in {@link TmpSubdir}
     * @throws IllegalStateException when a (sub)directory could not be deleted or created
     */
    public static @NotNull Map<TmpSubdir, File> prepareTmp() {
        if (conquadeTmpDir.exists()) {
            try {
                FileUtils.deleteDirectory(conquadeTmpDir);
            } catch (IOException e) {
                throw new IllegalStateException("Could not delete conquade tmp directory (\"%s\")!".formatted(conquadeTmpDir.getAbsolutePath()), e);
            }
        }
        if (!conquadeTmpDir.mkdirs())
            throw new IllegalStateException("Could not create conquade tmp directory (\"%s\")!".formatted(conquadeTmpDir.getAbsolutePath()));

        Map<TmpSubdir, File> subdirs = Arrays.stream(TmpSubdir.values())
                .collect(Collectors.toMap(
                        subdir -> subdir,
                        subdir -> Path.of(conquadeTmpDir.getAbsolutePath(), subdir.toString()).toFile()
                ));

        for (File file : subdirs.values())
            if (!file.mkdir())
                throw new IllegalStateException("Could not create conquade tmp subdirectory (\"%s\")!"
                        .formatted(file.getAbsolutePath()));

        return subdirs;
    }

    /**
     * Enum specifying all the Conquade temporary subdirectories created when calling {@link Conquade#prepareTmp()}.
     */
    public enum TmpSubdir {
        /**
         * Render temporary subdirectory.
         * <p>Stores the extracted video frames, extracted audio and rendered video when rendering or streaming.</p>
         */
        RENDER,
        /**
         * Play temporary subdirectory.
         * <p>Stores the unpacked rendered video and audio when playing.</p>
         */
        PLAY;


        /**
         * Returns the subdirectory enum constant name lowercase.
         *
         * @return the {@link Enum#name()} in lowercase
         */
        @Override
        public @NotNull String toString() {
            return name().toLowerCase();
        }
    }

    /**
     * A command execution wrapper to track the output log and exit codes.
     *
     * @param command the command to execute
     * @return the command exit code
     * @throws IllegalStateException when the command can not be executed or when the return code is other than 0
     */
    public static int exec(final @NotNull String command) {
        ProcessBuilder processBuilder = new ProcessBuilder(command.split("\\s+"));
        processBuilder.redirectErrorStream(true);
        try {
            LOGGER.fine("$ %s".formatted(command));
            final Process process = processBuilder.start();
            PROCESSES.add(process);
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            IOUtils.copy(process.getInputStream(), baos);
            int code = process.waitFor();
            PROCESSES.remove(process);
            if (code != 0) {
                final String log = baos.toString();
                LOGGER.severe("Command failed with code %d!%nCommand:%n%s%nOutput log:%n%s"
                        .formatted(
                                code,
                                '\t' + command,
                                '\t' + log
                                        .substring(0, log.length() - 1) // remove last \n
                                        .replace("\n", "\n\t")
                        ));
                throw new IllegalStateException("Command failed with code %d!".formatted(code));
            }
            return code;
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Could not execute command!", e);
        }
    }
}
