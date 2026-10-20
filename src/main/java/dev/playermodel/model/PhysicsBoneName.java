package dev.playermodel.model;

import java.util.Locale;

/** Strict {@code xxx_physics} naming. */
public final class PhysicsBoneName {
    private PhysicsBoneName() {
    }

    public static boolean hasSuffix(String raw) {
        return raw != null && raw.trim().toLowerCase(Locale.ROOT).endsWith("_physics");
    }

    /** Returns the base name, or null for {@code _physics} / invalid names. */
    public static String baseName(String raw) {
        if (!hasSuffix(raw)) {
            return null;
        }
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        String base = lower.substring(0, lower.length() - "_physics".length());
        base = base.replaceAll("_+$", "");
        return base.isBlank() ? null : base;
    }

    public static String key(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }
}
