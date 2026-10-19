package dev.playermodel.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.playermodel.PlayerModelMod;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir()
            .resolve(PlayerModelMod.MOD_ID + "_server").resolve("config.json");

    public boolean enabled = true;
    public boolean allowUploads = true;
    public int maxModelBytes = 4 * 1024 * 1024;
    public int maxCacheBytes = 256 * 1024 * 1024;
    public boolean logUploads = true;

    public static ServerConfig load() {
        ServerConfig config = new ServerConfig();
        try {
            if (Files.isRegularFile(PATH)) {
                ServerConfig loaded = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8), ServerConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            }
        } catch (Exception e) {
            PlayerModelMod.LOG.warn("Failed to load server config", e);
        }
        config.maxModelBytes = Math.max(64 * 1024, Math.min(8 * 1024 * 1024, config.maxModelBytes));
        config.maxCacheBytes = Math.max(config.maxModelBytes, config.maxCacheBytes);
        config.save();
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (Exception e) {
            PlayerModelMod.LOG.warn("Failed to save server config", e);
        }
    }

    public static Path baseDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(PlayerModelMod.MOD_ID + "_server");
    }
}
