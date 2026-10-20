package dev.playermodel.util;

/**
 * Removes JSONC-style comments from {@code meta.json} while preserving quoted strings, so models
 * can be documented with normal {@code //} and {@code /* ... *​/} comments.
 */
public final class JsonComments {
    private JsonComments() {
    }

    public static String strip(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length());
        boolean inString = false;
        boolean escape = false;
        boolean lineComment = false;
        boolean blockComment = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            char next = i + 1 < input.length() ? input.charAt(i + 1) : '\0';
            if (lineComment) {
                if (c == '\n' || c == '\r') {
                    lineComment = false;
                    out.append(c);
                }
                continue;
            }
            if (blockComment) {
                if (c == '*' && next == '/') {
                    blockComment = false;
                    i++;
                } else if (c == '\n' || c == '\r') {
                    out.append(c);
                }
                continue;
            }
            if (inString) {
                out.append(c);
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                out.append(c);
            } else if (c == '/' && next == '/') {
                lineComment = true;
                i++;
            } else if (c == '/' && next == '*') {
                blockComment = true;
                i++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
