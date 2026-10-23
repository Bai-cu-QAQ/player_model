package dev.playermodel.util;

/** Removes line and block comments outside quoted JSON strings. */
public final class JsonComments {
    private JsonComments() {
    }

    public static String strip(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length());
        boolean inString = false;
        boolean escaped = false;
        boolean line = false;
        boolean block = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            char next = i + 1 < input.length() ? input.charAt(i + 1) : 0;
            if (line) {
                if (c == '\n' || c == '\r') {
                    line = false;
                    out.append(c);
                }
                continue;
            }
            if (block) {
                if (c == '*' && next == '/') {
                    block = false;
                    i++;
                }
                continue;
            }
            if (inString) {
                out.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                out.append(c);
            } else if (c == '/' && next == '/') {
                line = true;
                i++;
            } else if (c == '/' && next == '*') {
                block = true;
                i++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
