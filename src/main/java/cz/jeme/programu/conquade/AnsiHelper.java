package cz.jeme.programu.conquade;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

/**
 * Helper methods and constants for using ANSI codes.
 */
public final class AnsiHelper {
    /**
     * Escape character.
     */
    public static final char ESC = (char) 27;

    private AnsiHelper() {
        throw new AssertionError();
    }

    /**
     * Transforms rgb color provided as three separate int color channel values to ANSI color.
     *
     * @param red   the red channel color value
     * @param green the green channel color value
     * @param blue  the blue channel color value
     * @return transformed ANSI color
     */
    @Range(from = 16, to = 255)
    public static int toAnsi256(final int red, final int green, final int blue) {
        return 16
                + 36 * Math.round(red / 255f * 5)
                + 6 * Math.round(green / 255f * 5)
                + Math.round(blue / 255f * 5);
    }


    /**
     * Returns an ANSI code to switch the font color.
     *
     * @param ansiColor the ANSI color to use
     * @return the code to switch the font color
     */
    public static @NotNull String foregroundColor256(final int ansiColor) {
        return "%c[38;5;%sm".formatted(ESC, ansiColor);
    }

    /**
     * Returns an ANSI code to switch the background color.
     *
     * @param ansiColor the ANSI color to use
     * @return the code to switch the background color
     */
    public static @NotNull String backgroundColor256(final int ansiColor) {
        return "%c[48;5;%sm".formatted(ESC, ansiColor);
    }

    /**
     * Returns an ANSI code to switch the font color. Requires the terminal to have truecolor support.
     *
     * @param red   the red RGB color channel value
     * @param green the green RGB color channel value
     * @param blue  the blue RGB color channel value
     * @return the code to switch the font color
     */
    public static @NotNull String foregroundColorRGB(final int red, final int green, final int blue) {
        return "%c[38;2;%d;%d;%dm".formatted(ESC, red, green, blue);
    }

    /**
     * Returns an ANSI code to switch the background color. Requires the terminal to have truecolor support.
     *
     * @param red   the red RGB color channel value
     * @param green the green RGB color channel value
     * @param blue  the blue RGB color channel value
     * @return the code to switch the background color
     */
    public static @NotNull String backgroundColorRGB(final int red, final int green, final int blue) {
        return "%c[48;2;%d;%d;%dm".formatted(ESC, red, green, blue);
    }

    /**
     * A code to show the cursor.
     */
    public static final @NotNull String SHOW_CURSOR = ESC + "[?25h";
    /**
     * A code to hide the cursor.
     */
    public static final @NotNull String HIDE_CURSOR = ESC + "[?25l";
    /**
     * A code to clear an entire line.
     */
    public static final @NotNull String CLEAR_LINE = ESC + "[2K";
    /**
     * A code to reset font color, background color and font weight.
     */
    public static final @NotNull String RESET = ESC + "[0m";

    /**
     * Returns a code to move the cursor up.
     *
     * @param lines how many lines up should the cursor move
     * @return the code to move the cursor
     */
    public static @NotNull String moveCursorUp(final int lines) {
        return "%s[%dA".formatted(ESC, lines);
    }

    /**
     * Returns a code to move the cursor down.
     *
     * @param lines how many lines down should the cursor move
     * @return the code to move the cursor
     */
    public static @NotNull String moveCursorDown(final int lines) {
        return "%s[%dB".formatted(ESC, lines);
    }

    /**
     * A method to obtain separate color channels (red, green, blue) from an rgb value represented as int.
     *
     * @param rgb the rgb value to be separated
     * @return an array containing red, green and blue channel values
     */
    public static int[] splitRGB(final int rgb) {
        return new int[]{(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF};
    }

    /**
     * A method to merge separate color channels (red, green, blue) to one rgb value represented as int.
     *
     * @param red   the red color channel value
     * @param green the green color channel value
     * @param blue  the blue color channel value
     * @return rgb value represented as int
     */
    public static int joinRGB(final int red, final int green, final int blue) {
        int redc = Math.min(255, Math.max(0, red));
        int greenc = Math.min(255, Math.max(0, green));
        int bluec = Math.min(255, Math.max(0, blue));
        return (redc << 16) | (greenc << 8) | bluec;
    }
}
