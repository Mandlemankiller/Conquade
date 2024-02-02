package cz.jeme.programu.conquade;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Conquade commandline arguments parser.
 */
public final class ConquadeArgs {
    private final @NotNull Action action;
    private final @NotNull Map<String, String> argMap;

    /**
     * Creates a new instance of {@link ConquadeArgs} from the provided array
     *
     * @param args the commandline args represented as a string array
     * @throws IllegalArgumentException when the action argument does not represent any existing {@link Action},
     *                                  when an invalid argument is passed or when an argument is passed multiple times
     */
    public ConquadeArgs(final String @NotNull [] args) {
        final String actionStr = args.length == 0 ? "HELP" : args[0].toUpperCase();
        try {
            action = Action.valueOf(actionStr);
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

    /**
     * Returns a key-value map of the arguments.
     * <p>For example:</p>
     * -i file.mp4 -fps 25
     * <p>will be returned as</p>
     * key: "i"; value: "file.mp4"
     * <p>key: "fps"; value: "25"</p>
     *
     * @return the map of the arguments
     */
    public @NotNull Map<String, String> getArgMap() {
        return argMap;
    }

    /**
     * Returns the action. If no action was provided, this will return {@link Action#HELP}.
     *
     * @return the action
     */
    public @NotNull Action getAction() {
        return action;
    }

    /**
     * Represents a Conquade action.
     */
    public enum Action {
        /**
         * Print out the usage info.
         */
        HELP,
        /**
         * Render a video to a Conquade file.
         */
        RENDER,
        /**
         * Play a rendered video from a Conquade file.
         */
        PLAY,
        /**
         * Play a video without rendering it.
         */
        STREAM;

        /**
         * Returns the lowercase enum constant name.
         *
         * @return {@link Enum#name()} but lowercase
         */
        @Override
        public @NotNull String toString() {
            return name().toLowerCase();
        }
    }
}
