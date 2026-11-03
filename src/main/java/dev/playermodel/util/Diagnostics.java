package dev.playermodel.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts precise positions from Gson / parser failures for user-facing diagnostics. */
public final class Diagnostics {
    private static final Pattern LINE_COLUMN = Pattern.compile("line\\s+(\\d+)\\s+column\\s+(\\d+)");
    private static final Pattern PATH = Pattern.compile("path\\s+(\\$[^\\s,;)]*)");

    private Diagnostics() {
    }

    /** Returns "第 N 行，第 M 列" plus the JSON path when the exception carries them. */
    public static String jsonPosition(Throwable error) {
        String message = messages(error);
        StringBuilder result = new StringBuilder();
        Matcher position = LINE_COLUMN.matcher(message);
        if (position.find()) {
            result.append("第 ").append(position.group(1)).append(" 行，第 ")
                    .append(position.group(2)).append(" 列");
        }
        Matcher path = PATH.matcher(message);
        if (path.find()) {
            if (result.length() > 0) {
                result.append("，");
            }
            result.append("JSON 路径 ").append(path.group(1));
        }
        return result.toString();
    }

    /** Gson wraps the useful message in causes; join them so regexes see the innermost message too. */
    public static String messages(Throwable error) {
        StringBuilder all = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < 8) {
            if (current.getMessage() != null) {
                all.append(current.getMessage()).append('\n');
            }
            current = current.getCause();
        }
        return all.toString();
    }

    /** A short parser description without Gson's Java exception class prefixes. */
    public static String jsonReason(Throwable error) {
        String message = messages(error);
        int marker = message.indexOf("Expected ");
        if (marker >= 0) {
            String tail = message.substring(marker).replaceAll("\\s+at\\s+line\\s+\\d+.*$", "");
            return tail.trim();
        }
        String first = message.strip().split("\\R", 2)[0];
        return first.replaceFirst("^java\\.(?:lang\\.)?[A-Za-z]+Exception:\\s*", "");
    }
}
