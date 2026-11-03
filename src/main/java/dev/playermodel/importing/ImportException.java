package dev.playermodel.importing;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Thrown when a Blockbench model cannot be imported. The message is user-facing and carries a
 * location chain such as {@code elements[3] "head" (uuid) > faces.up.rotation}.
 */
public class ImportException extends Exception {
    private final String detail;
    private final String location;
    private final String source;

    public ImportException(String message) {
        this(message, null, null, null);
    }

    public ImportException(String message, Throwable cause) {
        this(message, cause, null, null);
    }

    private ImportException(String detail, Throwable cause, String source, String location) {
        super(detail, cause);
        this.detail = detail;
        this.location = location;
        this.source = source;
    }

    /** Adds an outer location, e.g. the element or group that contains the failing field. */
    public ImportException at(String outerLocation) {
        if (outerLocation == null || outerLocation.isBlank()) {
            return this;
        }
        String combined = location == null || location.isBlank()
                ? outerLocation : outerLocation + " > " + location;
        return new ImportException(detail, getCause(), source, combined);
    }

    public ImportException from(Path file) {
        return new ImportException(detail, getCause(), file == null ? source : file.toString(), location);
    }

    public String detail() {
        return detail;
    }

    public String location() {
        return location;
    }

    public String source() {
        return source;
    }

    @Override
    public String getMessage() {
        StringBuilder message = new StringBuilder();
        if (source != null && !source.isBlank()) {
            message.append(source);
        }
        if (location != null && !location.isBlank()) {
            if (message.length() > 0) {
                message.append(" -> ");
            }
            message.append(location);
        }
        if (message.length() > 0) {
            message.append(": ");
        }
        return message.append(detail).toString();
    }

    public static String describe(ImportException error, Path file) {
        if (error.source() == null && file != null) {
            return error.from(file).getMessage();
        }
        return Objects.requireNonNullElse(error.getMessage(), error.detail());
    }
}
