package cz.jeme.programu.conquade;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

public final class AnsiHelper {
    public static final char ESC = (char) 27;

    private AnsiHelper() {
        throw new AssertionError();
    }

    @Range(from = 16, to = 255)
    public static int toAnsi(final int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return toAnsi(red, green, blue);
    }

    @Range(from = 16, to = 255)
    public static int toAnsi(final int red, final int green, final int blue) {
        return 16
                + 36 * Math.round(red / 255f * 5)
                + 6 * Math.round(green / 255f * 5)
                + Math.round(blue / 255f * 5);
    }

    public static @NotNull String foregroundColor(int color) {
        return "%c[38;5;%sm".formatted(ESC, color);
    }

    public static @NotNull String backgroundColor(int color) {
        return "%c[48;5;%sm".formatted(ESC, color);
    }

    public static final @NotNull String SHOW_CURSOR = ESC + "[?25h";
    public static final @NotNull String HIDE_CURSOR = ESC + "[?25l";
    public static final @NotNull String CLEAR_LINE = ESC + "[2K";
    public static final @NotNull String RESET = ESC + "[0m";

    public static @NotNull String moveCursorUp(int lines) {
        return "%s[%dA".formatted(ESC, lines);
    }

    public static @NotNull String moveCursorDown(int lines) {
        return "%s[%dB".formatted(ESC, lines);
    }

    public static int[] splitRGB(final int rgb) {
        return new int[]{(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF};
    }

    public static int joinRGB(final int red, final int green, final int blue) {
        int redc = Math.min(255, Math.max(0, red));
        int greenc = Math.min(255, Math.max(0, green));
        int bluec = Math.min(255, Math.max(0, blue));
        return (redc << 16) | (greenc << 8) | bluec;
    }
}
