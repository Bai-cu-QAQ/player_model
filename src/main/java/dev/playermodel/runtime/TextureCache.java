package dev.playermodel.runtime;

import dev.playermodel.PlayerModelMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

/** Lazily registers model textures and re-registers them after a resource reload. */
public final class TextureCache {
    private static final Map<String, Identifier> REGISTERED = new HashMap<>();

    private TextureCache() {
    }

    public static Identifier id(String hash) {
        return Identifier.of(PlayerModelMod.MOD_ID, "model/" + hash.substring(0, Math.min(32, hash.length())));
    }

    public static synchronized Identifier ensure(String hash, byte[] png) {
        Identifier existing = REGISTERED.get(hash);
        if (existing != null) {
            var current = MinecraftClient.getInstance().getTextureManager().getOrDefault(existing, null);
            if (current instanceof NativeImageBackedTexture nativeTexture
                    && nativeTexture.getImage() != null
                    && !current.getClass().getName().contains("MissingSprite")) {
                return existing;
            }
            REGISTERED.remove(hash);
        }
        if (!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()) {
            MinecraftClient.getInstance().execute(() -> ensure(hash, png));
            return null;
        }
        try {
            TextureManager manager = MinecraftClient.getInstance().getTextureManager();
            NativeImage image = NativeImage.read(new ByteArrayInputStream(png));
            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            Identifier id = id(hash);
            manager.registerTexture(id, texture);
            texture.setFilter(false, false);
            REGISTERED.put(hash, id);
            var stored = manager.getOrDefault(id, null);
            PlayerModelMod.LOG.info("Registered model texture {} -> {} (stored={}, image={})", hash.substring(0, 8), id,
                    stored == null ? "null" : stored.getClass().getSimpleName(),
                    stored instanceof NativeImageBackedTexture t ? (t.getImage() != null) : false);
            return id;
        } catch (Throwable t) {
            PlayerModelMod.LOG.warn("Failed to register model texture {}", hash, t);
            return null;
        }
    }

    public static synchronized boolean ready(String hash) {
        return REGISTERED.containsKey(hash);
    }

    public static synchronized void clear() {
        REGISTERED.clear();
    }
}
