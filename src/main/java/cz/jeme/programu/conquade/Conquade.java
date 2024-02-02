package cz.jeme.programu.conquade;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.*;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.*;
import java.util.stream.Collectors;

public final class Conquade {
    // Max 255 characters to prevent byte overflow!
    public static final @NotNull String CHARACTERS = " ._-:!?71iIca234db56O089$W%#@Ñ";
    public static final char @NotNull [] CHARACTERS_ARRAY = CHARACTERS.toCharArray();
    public static final @NotNull String FILE_EXTENSION = ".cqd";
    public static final @NotNull Logger LOGGER = Logger.getLogger(Conquade.class.getName());
    public static final int TERMINAL_WIDTH;
    public static final int TERMINAL_HEIGHT;
    public static @NotNull File conquadeTmpDir;
    private static final @NotNull Set<Process> PROCESSES = new HashSet<>();

    public static boolean debug = false;

    static {
        try {
            Terminal terminal = TerminalBuilder.terminal();
            TERMINAL_WIDTH = terminal.getWidth();
            TERMINAL_HEIGHT = terminal.getHeight() - 1; // reserved for screen overflow
        } catch (IOException e) {
            throw new IllegalStateException("Could not detect terminal size!", e);
        }

        final String systemTmpDirPath = System.getProperty("java.io.tmpdir");
        if (systemTmpDirPath == null)
            throw new IllegalStateException("Could not detect system tmp directory!");
        conquadeTmpDir = Path.of(systemTmpDirPath, "conquade").toFile();

        if (CHARACTERS.length() > 255)
            throw new IllegalStateException("The maximum number of characters is 255!");
    }

    public static @NotNull String ffmpegExe = "ffmpeg";

    public static void enableLogger() {
        LOGGER.setLevel(debug ? Level.FINE : Level.INFO);
    }

    public static void disableLogger() {
        LOGGER.setLevel(Level.OFF);
    }


    public static void main(final String @NotNull [] args) {
        LogManager.getLogManager().reset();
        final Handler logHandler = new ConsoleHandler();
        logHandler.setLevel(Level.FINEST);
        logHandler.setFormatter(ConquadeFormatter.getInstance());
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

        if (conquadeArgs.getArgMap().containsKey("debug")) {
            debug = true;
            enableLogger();
            LOGGER.fine("Debug mode enabled.");
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
        }
    }

    public static void logError(final @NotNull Level level, final @NotNull String message, final @NotNull Throwable throwable) {
        final StringWriter stringWriter = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(stringWriter);
        throwable.printStackTrace(printWriter);
        String stackTraceStr = stringWriter.toString();
        stackTraceStr = stackTraceStr.substring(0, stackTraceStr.length() - 1); // Remove last newline
        LOGGER.log(level, message + '\n' + stackTraceStr);
    }

    public static void logError(final @NotNull String message, final @NotNull Throwable throwable) {
        logError(Level.SEVERE, message, throwable);
    }

    public static void logError(final @NotNull Throwable throwable) {
        logError("", throwable);
    }


    public static void validateInputFile(final @NotNull File inputFile) {
        if (!inputFile.exists())
            throw new IllegalArgumentException("Input file (\"%s\") does not exist!".formatted(inputFile.getAbsolutePath()));
    }

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

    public enum TmpSubdir {
        RENDER,
        PLAY;


        @Override
        public @NotNull String toString() {
            return name().toLowerCase();
        }
    }

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
