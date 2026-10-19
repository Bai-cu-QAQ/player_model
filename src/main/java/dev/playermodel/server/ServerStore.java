package dev.playermodel.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.playermodel.PlayerModelMod;
import dev.playermodel.model.ModelBundle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-side state and model cache. Models are keyed by SHA-256 and persisted in the config dir. */
public final class ServerStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final ServerConfig config;
    private final Path cacheDir;
    private final Path statesFile;
    private final Map<UUID, State> states = new LinkedHashMap<>();
    private final Map<String, byte[]> models = new HashMap<>();
    private final Map<String, Long> lastAccess = new HashMap<>();
    private long totalBytes;

    public ServerStore(ServerConfig config) {
        this.config = config;
        this.cacheDir = ServerConfig.baseDir().resolve("cache");
        this.statesFile = ServerConfig.baseDir().resolve("states.json");
        load();
    }

    private void load() {
        try {
            Files.createDirectories(cacheDir);
            if (Files.isRegularFile(statesFile)) {
                Map<String, State> raw = GSON.fromJson(Files.readString(statesFile, StandardCharsets.UTF_8),
                        new TypeToken<Map<String, State>>() {}.getType());
                if (raw != null) {
                    raw.forEach((key, state) -> {
                        try {
                            states.put(UUID.fromString(key), state);
                        } catch (IllegalArgumentException ignored) {
                        }
                    });
                }
            }
            try (var stream = Files.list(cacheDir)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".pmb")).forEach(file -> {
                    try {
                        byte[] bytes = Files.readAllBytes(file);
                        if (bytes.length > config.maxModelBytes) {
                            return;
                        }
                        ModelBundle bundle = ModelBundle.fromBytes(bytes);
                        models.put(bundle.hash, bytes);
                        lastAccess.put(bundle.hash, file.toFile().lastModified());
                        totalBytes += bytes.length;
                    } catch (Exception e) {
                        PlayerModelMod.LOG.warn("Ignoring invalid server cache file {}", file);
                    }
                });
            }
            evictIfNeeded();
        } catch (IOException e) {
            PlayerModelMod.LOG.warn("Failed to load server model store", e);
        }
    }

    public synchronized State state(UUID uuid) {
        return states.get(uuid);
    }

    public synchronized void setState(UUID uuid, boolean enabled, String hash, String name) {
        State state = new State(uuid, enabled, hash == null ? "" : hash, name == null ? "" : name);
        states.put(uuid, state);
        saveStates();
    }

    public synchronized List<State> snapshot() {
        return new ArrayList<>(states.values());
    }

    public synchronized boolean hasModel(String hash) {
        return models.containsKey(hash);
    }

    public synchronized byte[] model(String hash) {
        lastAccess.put(hash, System.currentTimeMillis());
        return models.get(hash);
    }

    public synchronized boolean putModel(String expectedHash, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > config.maxModelBytes) {
            return false;
        }
        ModelBundle bundle;
        try {
            bundle = ModelBundle.fromBytes(bytes);
        } catch (Exception e) {
            return false;
        }
        if (!bundle.hash.equals(expectedHash)) {
            return false;
        }
        if (models.containsKey(bundle.hash)) {
            return true;
        }
        models.put(bundle.hash, bytes);
        totalBytes += bytes.length;
        lastAccess.put(bundle.hash, System.currentTimeMillis());
        try {
            Files.write(cacheDir.resolve(bundle.hash + ".pmb"), bytes);
        } catch (IOException e) {
            PlayerModelMod.LOG.warn("Failed to persist model {}", bundle.hash, e);
        }
        evictIfNeeded();
        return true;
    }

    private void evictIfNeeded() {
        if (totalBytes <= config.maxCacheBytes) {
            return;
        }
        List<String> sorted = new ArrayList<>(models.keySet());
        sorted.sort(Comparator.comparingLong(hash -> lastAccess.getOrDefault(hash, 0L)));
        for (String hash : sorted) {
            if (totalBytes <= config.maxCacheBytes) {
                break;
            }
            byte[] removed = models.remove(hash);
            if (removed != null) {
                totalBytes -= removed.length;
            }
            lastAccess.remove(hash);
            try {
                Files.deleteIfExists(cacheDir.resolve(hash + ".pmb"));
            } catch (IOException ignored) {
            }
        }
    }

    public synchronized void saveStates() {
        try {
            Files.createDirectories(statesFile.getParent());
            Map<String, State> raw = new LinkedHashMap<>();
            states.forEach((uuid, state) -> raw.put(uuid.toString(), state));
            Files.writeString(statesFile, GSON.toJson(raw), StandardCharsets.UTF_8);
        } catch (IOException e) {
            PlayerModelMod.LOG.warn("Failed to save player states", e);
        }
    }

    public synchronized int modelCount() {
        return models.size();
    }

    public synchronized long modelBytes() {
        return totalBytes;
    }

    public record State(UUID uuid, boolean enabled, String hash, String name) {
    }
}
