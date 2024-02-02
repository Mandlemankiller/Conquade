package cz.jeme.programu.conquade;

import org.jetbrains.annotations.NotNull;

/**
 * Helper methods for constructing colorful ANSI characters.
 */
public final class AnsiChar {
    private AnsiChar() {
        throw new AssertionError();
    }

    private static final @NotNull StringBuilder literalBuilder = new StringBuilder();

    /**
     * A literal character ANSI code with the provided foreground and background color.
     * <p>Pass -1 to disable any of the colors.</p>
     *
     * @param content        the character
     * @param ansiForeground the foreground color
     * @param ansiBackground the background color
     * @return the ANSI code to print the colored character
     */
    public static @NotNull String literal256(final char content, final int ansiForeground, final int ansiBackground) {
        literalBuilder.setLength(0);
        if (ansiForeground != -1)
            literalBuilder.append(AnsiHelper.foregroundColor256(ansiForeground));
        if (ansiBackground != -1)
            literalBuilder.append(AnsiHelper.backgroundColor256(ansiBackground));
        literalBuilder.append(content).append(AnsiHelper.RESET);
        return literalBuilder.toString();
    }

    /**
     * A literal character ANSI code with the provided foreground color.
     * <p>Pass -1 to disable the foreground color.</p>
     *
     * @param content        the character
     * @param ansiForeground the foreground color
     * @return the ANSI code to print the colored character
     */
    public static @NotNull String literal256(final char content, final int ansiForeground) {
        return literal256(content, ansiForeground, -1);
    }

    /**
     * A literal character ANSI code with the provided foreground and background RGB color.
     * <p>Pass -1 to disable any of the colors.</p>
     * <p>Requires the terminal to have truecolor support.</p>
     *
     * @param content the character
     * @param fgRed   the foreground red RGB channel value
     * @param fgGreen the foreground green RGB channel value
     * @param fgBlue  the foreground blue RGB channel value
     * @param bgRed   the background red RGB channel value
     * @param bgGreen the background green RGB channel value
     * @param bgBlue  the background blue RGB channel value
     * @return the ANSI code to print the colored character
     */
    public static @NotNull String literalRGB(final char content,
                                             final int fgRed, final int fgGreen, final int fgBlue,
                                             final int bgRed, final int bgGreen, final int bgBlue) {
        literalBuilder.setLength(0);
        if (fgRed != -1 && fgGreen != -1 && fgBlue != -1)
            literalBuilder.append(AnsiHelper.foregroundColorRGB(fgRed, fgGreen, fgBlue));
        if (bgRed != -1 && bgGreen != -1 && bgBlue != -1)
            literalBuilder.append(AnsiHelper.backgroundColorRGB(bgRed, bgGreen, bgBlue));
        literalBuilder.append(content).append(AnsiHelper.RESET);
        return literalBuilder.toString();
    }

    /**
     * A literal character ANSI code with the provided foreground RGB color.
     * <p>Pass -1 to disable the foreground color.</p>
     * <p>Requires the terminal to have truecolor support.</p>
     *
     * @param content the character
     * @param fgRed   the foreground red RGB channel value
     * @param fgGreen the foreground green RGB channel value
     * @param fgBlue  the foreground blue RGB channel value
     * @return the ANSI code to print the colored character
     */
    public static @NotNull String literalRGB(final char content,
                                             final int fgRed, final int fgGreen, final int fgBlue) {
        return literalRGB(content, fgRed, fgGreen, fgBlue, -1, -1, -1);
    }
}
