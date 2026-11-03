package dev.playermodel.jpm;

/** Parse/validation error for a JEM, JPM or expression. */
public class JpmException extends Exception {
    public JpmException(String message) {
        super(message);
    }
    public JpmException(String message, Throwable cause) {
        super(message, cause);
    }
}
