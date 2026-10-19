package dev.playermodel.importing;

/** Thrown when a Blockbench model cannot be imported. The message is user-facing. */
public class ImportException extends Exception {
    public ImportException(String message) {
        super(message);
    }

    public ImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
