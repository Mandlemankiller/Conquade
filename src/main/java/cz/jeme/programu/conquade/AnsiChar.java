package cz.jeme.programu.conquade;

import org.jetbrains.annotations.NotNull;

public final class AnsiChar {
    private AnsiChar() {
        throw new AssertionError();
    }

    private static final @NotNull StringBuilder literalBuilder = new StringBuilder();

    public static @NotNull String literal(char content, int ansiForeground, int ansiBackground) {
        literalBuilder.setLength(0);
        if (ansiForeground != -1)
            literalBuilder.append(AnsiHelper.foregroundColor(ansiForeground));
        if (ansiBackground != -1)
            literalBuilder.append(AnsiHelper.backgroundColor(ansiBackground));
        literalBuilder.append(content);
        // Reset
        literalBuilder.append(AnsiHelper.RESET);
        return literalBuilder.toString();
    }

    public static @NotNull String literal(char content, int ansiForeground) {
        return literal(content, ansiForeground, -1);
    }

    public static @NotNull String literal(char content) {
        return literal(content, -1);
    }
}
