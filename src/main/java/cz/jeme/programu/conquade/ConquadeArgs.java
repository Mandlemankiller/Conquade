package cz.jeme.programu.conquade;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class ConquadeArgs {
    private final @NotNull Action action;
    private final @NotNull Map<String, String> argMap;

    public ConquadeArgs(final String @NotNull [] args) {
        if (args.length == 0)
            throw new IllegalArgumentException("Missing action argument! " +
                    "Valid actions are: " + Arrays.toString(Action.values()));
        try {
            action = Action.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Action argument is not valid! " +
                            "Valid actions are: " + Arrays.toString(Action.values()), e);
        }
        // load the arg map
        Map<String, String> tempArgMap = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            String key = args[i];
            final String value = i == args.length - 1 ? null : args[i + 1];
            if (!key.startsWith("-"))
                throw new IllegalArgumentException(
                        ("\"%s\" is not a valid argument! " +
                                "Arguments must start with a hyphen!").formatted(key));
            key = key.substring(1); // Remove the hyphen
            if (tempArgMap.containsKey(key))
                throw new IllegalArgumentException("\"%s\" argument passed multiple times!".formatted(key));
            if (value == null || value.startsWith("-")) {
                tempArgMap.put(key, null);
            } else {
                tempArgMap.put(key, value);
                i++;
            }
        }
        argMap = Collections.unmodifiableMap(tempArgMap);
    }

    public @NotNull Map<String, String> getArgMap() {
        return argMap;
    }

    public @NotNull Action getAction() {
        return action;
    }

    public enum Action {
        HELP,
        RENDER,
        PLAY,
        STREAM;


        @Override
        public @NotNull String toString() {
            return name().toLowerCase();
        }
    }
}
