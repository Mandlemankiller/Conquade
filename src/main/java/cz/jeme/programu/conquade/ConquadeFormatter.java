package cz.jeme.programu.conquade;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public final class ConquadeFormatter extends Formatter {
    public static final @NotNull SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    public static final @NotNull DecimalFormat DECIMAL_FORMATTER = new DecimalFormat("#0.00");
    private static @Nullable ConquadeFormatter instance;

    private ConquadeFormatter() {
    }

    public static @NotNull ConquadeFormatter getInstance() {
        if (instance == null)
            instance = new ConquadeFormatter();
        return instance;
    }


    @Override
    public @NotNull String format(final @NotNull LogRecord record) {
        final String[] classPath = record.getSourceClassName().split("\\.+");
        final String className = classPath[classPath.length - 1].replace('$', ':');
        return "%s[%s %s]: [%s] %s%s%n".formatted(
                ConquadeLevel.getAnsi(record.getLevel()),
                DATE_FORMATTER.format(Date.from(record.getInstant())),
                ConquadeLevel.getName(record.getLevel()),
                className,
                record.getMessage(),
                AnsiHelper.RESET
        );
    }

    private enum ConquadeLevel {
        SEVERE("ERROR", 255, 0, 0),
        WARNING("WARN", 255, 255, 0),
        FINE("DEBUG", 100, 100, 100),
        DEFAULT(null, AnsiHelper.RESET);

        private final @NotNull String ansi;
        private final @Nullable String name;

        ConquadeLevel(final @Nullable String name, final int red, final int green, final int blue) {
            this(name, AnsiHelper.foregroundColor(AnsiHelper.toAnsi(red, green, blue)));
        }

        ConquadeLevel(final @Nullable String name, final @NotNull String ansi) {
            this.ansi = ansi;
            this.name = name;
        }

        public @NotNull String getAnsi() {
            return ansi;
        }

        public @Nullable String getName() {
            return name;
        }

        public static @NotNull String getAnsi(final @NotNull Level level) {
            return Arrays.stream(values())
                    .filter(levelColor -> levelColor.toString().equals(level.getName()))
                    .map(ConquadeLevel::getAnsi)
                    .findFirst()
                    .orElse(DEFAULT.getAnsi());
        }

        public static @NotNull String getName(final @NotNull Level level) {
            return Arrays.stream(values())
                    .filter(levelColor -> levelColor.toString().equals(level.getName()))
                    .findFirst()
                    .map(ConquadeLevel::getName)
                    .orElse(level.getName());
        }
    }
}