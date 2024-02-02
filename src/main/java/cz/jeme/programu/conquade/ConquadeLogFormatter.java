package cz.jeme.programu.conquade;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * A formatter to format log Conquade log messages.
 */
public final class ConquadeLogFormatter extends Formatter {
    /**
     * A date formatter that formats time in format yyyy-MM-dd HH:mm:ss.
     */
    public static final @NotNull SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    /**
     * A decimal formatter that rounds to two decimals.
     */
    public static final @NotNull DecimalFormat DECIMAL_FORMATTER = new DecimalFormat("#0.00");
    private static @Nullable ConquadeLogFormatter instance;

    private ConquadeLogFormatter() {
        // singleton
        if (instance != null)
            throw new AssertionError("Singleton can not be constructed multiple times!");
    }

    /**
     * Obtain the one and only instance of {@link ConquadeLogFormatter}.
     *
     * @return the {@link ConquadeLogFormatter} instance
     */
    public static @NotNull ConquadeLogFormatter getInstance() {
        if (instance == null)
            instance = new ConquadeLogFormatter();
        return instance;
    }


    @Override
    public @NotNull String format(final @NotNull LogRecord record) {
        final String[] classPath = record.getSourceClassName().split("\\.+");
        final String className = classPath[classPath.length - 1].replace('$', ':');
        return "%s[%s %s]: [%s] %s%s%n".formatted(
                ConquadeLevel.parseLevel(record.getLevel()).getAnsi(),
                DATE_FORMATTER.format(Date.from(record.getInstant())),
                Optional.ofNullable(ConquadeLevel.parseLevel(record.getLevel()).getName()).orElse(record.getLevel().getName()),
                className,
                record.getMessage(),
                AnsiHelper.RESET
        );
    }

    /**
     * A custom logger level system based on
     */
    private enum ConquadeLevel {
        /**
         * SEVERE - ERROR level, red color.
         */
        SEVERE("ERROR", 255, 0, 0),
        /**
         * WARNING - WARN level, yellow color.
         */
        WARNING("WARN", 255, 255, 0),
        /**
         * FINE - DEBUG level, gray color.
         */
        FINE("DEBUG", 100, 100, 100),
        /**
         * Fallback, resets the color.
         */
        DEFAULT(null, AnsiHelper.RESET);

        private final @NotNull String ansi;
        private final @Nullable String name;

        ConquadeLevel(final @Nullable String name, final int red, final int green, final int blue) {
            this(name, Conquade.trueColor
                    ? AnsiHelper.foregroundColorRGB(red, green, blue)
                    : AnsiHelper.foregroundColor256(AnsiHelper.toAnsi256(red, green, blue))
            );
        }

        ConquadeLevel(final @Nullable String name, final @NotNull String ansi) {
            this.ansi = ansi;
            this.name = name;
        }

        /**
         * Returns the ANSI code of this level.
         *
         * @return the ANSI code
         */
        public @NotNull String getAnsi() {
            return ansi;
        }

        /**
         * Returns the modified name of this level.
         *
         * @return the name
         */
        public @Nullable String getName() {
            return name;
        }

        /**
         * Parses a {@link Level} to a {@link ConquadeLevel}. If the provided level does not have a counterpart {@link ConquadeLevel#DEFAULT} is returned.
         *
         * @param level the level to find the counterpart to
         * @return the counterpart level or {@link ConquadeLevel#DEFAULT} if it does not exist.
         */
        public static @NotNull ConquadeLevel parseLevel(final @NotNull Level level) {
            return Arrays.stream(values())
                    .filter(levelColor -> levelColor.toString().equals(level.getName()))
                    .findFirst()
                    .orElse(DEFAULT);
        }
    }
}