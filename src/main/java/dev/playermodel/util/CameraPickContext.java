package dev.playermodel.util;

import net.minecraft.entity.Entity;

/** Thread-local only during vanilla crosshair picking, never changes general entity eyes. */
public final class CameraPickContext {
    public static final ThreadLocal<Entity> ENTITY = new ThreadLocal<>();
    private CameraPickContext() {}
}
