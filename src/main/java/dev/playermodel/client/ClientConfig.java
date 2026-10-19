package dev.playermodel.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.playermodel.PlayerModelMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Client-side configuration stored in config/player_model/config.json. */
public final class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir()
            .resolve(PlayerModelMod.MOD_ID).resolve("config.json");

    public boolean viewEnabled = true;
    public boolean selfEnabled = true;
    public String activeHash = "";
    public String activeName = "";
    public boolean offlineApplyToAll = false;
    public boolean debug = false;

    public static ClientConfig load() {
        ClientConfig config = new ClientConfig();
        try {
            if (Files.isRegularFile(PATH)) {
                ClientConfig loaded = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8), ClientConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            }
        } catch (Exception e) {
            PlayerModelMod.LOG.warn("Failed to load client config", e);
        }
        config.save();
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            PlayerModelMod.LOG.warn("Failed to save client config", e);
        }
    }
}
