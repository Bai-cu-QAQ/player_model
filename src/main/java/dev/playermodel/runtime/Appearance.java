package dev.playermodel.runtime;

/** The model chosen for one player (enabled flag + bundle hash). */
public record Appearance(String hash, boolean enabled, String displayName) {
}
