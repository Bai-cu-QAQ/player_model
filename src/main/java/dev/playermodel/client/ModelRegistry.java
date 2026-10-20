package dev.playermodel.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.playermodel.PlayerModelMod;
import dev.playermodel.compile.ModelCompiler;
import dev.playermodel.importing.BbmodelParser;
import dev.playermodel.importing.PngUtils;
import dev.playermodel.model.ModelBundle;
import dev.playermodel.model.NormalizedModel;
import dev.playermodel.model.PhysicsConfig;
import dev.playermodel.model.RawModel;
import dev.playermodel.model.RawTexture;
import dev.playermodel.runtime.PreparedModel;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Folder based model store. Local models live in
 * {@code config/playermodel/models/<name>/model.bbmodel (+ texture.png + meta.json)}.
 */
public final class ModelRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, LocalModel> local = new LinkedHashMap<>();
    private final Map<String, ModelBundle> remote = new LinkedHashMap<>();
    private final Map<String, PreparedModel> prepared = new LinkedHashMap<>();
    private final List<String> lastErrors = new ArrayList<>();

    public static Path modelsDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(PlayerModelMod.MOD_ID).resolve("models");
    }

    public static Path cacheDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(PlayerModelMod.MOD_ID).resolve("cache");
    }

    public synchronized void scanLocal() {
        local.clear();
        prepared.clear();
        lastErrors.clear();
        Path root = modelsDir();
        try {
            Files.createDirectories(root);
            // Folder models.
            try (Stream<Path> dirs = Files.list(root)) {
                dirs.filter(Files::isDirectory).forEach(this::loadFolder);
            }
            // Flat .bbmodel files directly in models/.
            try (Stream<Path> files = Files.list(root)) {
                files.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".bbmodel"))
                        .forEach(file -> loadFlat(file, root));
            }
        } catch (IOException e) {
            PlayerModelMod.LOG.warn("Failed to scan model folder", e);
        }
        loadCache();
    }

    private void loadFolder(Path folder) {
        Path bbmodel = folder.resolve("model.bbmodel");
        if (!Files.isRegularFile(bbmodel)) {
            try (Stream<Path> files = Files.list(folder)) {
                bbmodel = files.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".bbmodel"))
                        .findFirst().orElse(null);
            } catch (IOException e) {
                return;
            }
        }
        if (bbmodel == null) {
            return;
        }
        load(folder.getFileName().toString(), bbmodel, folder);
    }

    private void loadFlat(Path file, Path root) {
        String name = file.getFileName().toString();
        name = name.substring(0, name.length() - ".bbmodel".length());
        load(name, file, root);
    }

    private void load(String name, Path bbmodel, Path folder) {
        String safeName = sanitize(name);
        try {
            Path metaFile = folder.resolve("meta.json");
            Meta meta = new Meta();
            if (Files.isRegularFile(metaFile)) {
                Meta parsed = GSON.fromJson(Files.readString(metaFile), Meta.class);
                if (parsed != null) {
                    meta = parsed;
                }
            }
            RawModel raw = BbmodelParser.parse(bbmodel, meta.displayName == null || meta.displayName.isBlank()
                    ? safeName : meta.displayName);

            Path textureFile = folder.resolve("texture.png");
            byte[] png = raw.texture.png();
            if (Files.isRegularFile(textureFile)) {
                if (raw.textures.size() > 1) {
                    raw.warnings.add("检测到 texture.png，但该模型使用多张贴图；已忽略外部 texture.png，使用 bbmodel 内嵌贴图集");
                } else {
                    byte[] external = Files.readAllBytes(textureFile);
                    int[] size = PngUtils.readSize(external);
                    int uvW = meta.textureWidth > 0 ? meta.textureWidth : raw.textureWidth;
                    int uvH = meta.textureHeight > 0 ? meta.textureHeight : raw.textureHeight;
                    RawTexture replacement = new RawTexture("texture.png", external, size[0], size[1], uvW, uvH);
                    raw.texture = replacement;
                    raw.textures.clear();
                    raw.textures.add(replacement);
                    raw.textureWidth = uvW;
                    raw.textureHeight = uvH;
                    png = external;
                }
            }

            String textureMode = meta.textureMode == null || meta.textureMode.isBlank() ? "model" : meta.textureMode;
            String pivotMode = meta.pivotMode == null || meta.pivotMode.isBlank() ? "model" : meta.pivotMode;
            NormalizedModel model = ModelCompiler.compile(raw, textureMode, pivotMode);
            model.customAnchors = meta.customAnchors;
            if (meta.physics != null) {
                model.physics = meta.physics;
            }
            model.doubleLayer = meta.doubleLayer;
            model.slim = meta.slim || model.slim;
            if (meta.anchors != null && !meta.anchors.isEmpty()) {
                model.anchors = applyAnchorOverrides(model.anchors, meta.anchors);
            }
            if (model.texturePng != null) {
                png = model.texturePng;
            }
            ModelBundle bundle = ModelBundle.create(model, png);
            LocalModel localModel = new LocalModel(safeName,
                    meta.displayName == null || meta.displayName.isBlank() ? safeName : meta.displayName,
                    folder, model, png, bundle, List.copyOf(raw.warnings));
            local.put(safeName, localModel);
        } catch (Exception e) {
            String message = safeName + ": " + e.getMessage();
            lastErrors.add(message);
            PlayerModelMod.LOG.warn("Failed to load model folder {}", folder, e);
        }
    }

    private void loadCache() {
        Path root = cacheDir();
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".pmb")).forEach(file -> {
                try {
                    ModelBundle bundle = ModelBundle.fromBytes(Files.readAllBytes(file));
                    remote.putIfAbsent(bundle.hash, bundle);
                } catch (Exception e) {
                    PlayerModelMod.LOG.warn("Skipping invalid cached model {}", file);
                }
            });
        } catch (IOException e) {
            PlayerModelMod.LOG.warn("Failed to scan model cache", e);
        }
    }

    public synchronized Collection<LocalModel> localModels() {
        return List.copyOf(local.values());
    }

    public synchronized Map<String, String> namedModels() {
        Map<String, String> result = new LinkedHashMap<>();
        local.forEach((name, model) -> result.put(name, model.bundle.hash));
        return result;
    }

    public synchronized List<String> errors() {
        return List.copyOf(lastErrors);
    }

    public synchronized LocalModel localByName(String name) {
        if (name == null) {
            return null;
        }
        LocalModel direct = local.get(name);
        if (direct != null) {
            return direct;
        }
        for (LocalModel model : local.values()) {
            if (model.name.equalsIgnoreCase(name) || model.displayName.equalsIgnoreCase(name)) {
                return model;
            }
        }
        return null;
    }

    public synchronized LocalModel localByHash(String hash) {
        for (LocalModel model : local.values()) {
            if (model.bundle.hash.equals(hash)) {
                return model;
            }
        }
        return null;
    }

    public synchronized String hashForName(String nameOrHash) {
        LocalModel model = localByName(nameOrHash);
        if (model != null) {
            return model.bundle.hash;
        }
        if (remote.containsKey(nameOrHash)) {
            return nameOrHash;
        }
        return null;
    }

    public synchronized String nameForHash(String hash) {
        for (LocalModel model : local.values()) {
            if (model.bundle.hash.equals(hash)) {
                return model.name;
            }
        }
        return hash;
    }

    /** Resolves the active model name after a folder scan, updating the hash if the file changed. */
    public synchronized String resolveActiveHash(String activeName, String activeHash) {
        if (activeName != null && !activeName.isBlank()) {
            LocalModel model = localByName(activeName);
            if (model != null) {
                return model.bundle.hash;
            }
        }
        if (activeHash != null && !activeHash.isBlank()) {
            if (localByHash(activeHash) != null || remote.containsKey(activeHash)) {
                return activeHash;
            }
        }
        return "";
    }

    public synchronized ModelBundle bundle(String hash) {
        LocalModel localModel = localByHash(hash);
        if (localModel != null) {
            return localModel.bundle;
        }
        return remote.get(hash);
    }

    public synchronized PreparedModel prepared(String hash) {
        if (hash == null) {
            return null;
        }
        PreparedModel cached = prepared.get(hash);
        if (cached != null) {
            return cached;
        }
        LocalModel localModel = localByHash(hash);
        if (localModel != null) {
            PreparedModel result = localModel.prepared();
            prepared.put(hash, result);
            return result;
        }
        ModelBundle bundle = remote.get(hash);
        if (bundle != null) {
            PreparedModel result = new PreparedModel(bundle.model);
            prepared.put(hash, result);
            return result;
        }
        return null;
    }

    public synchronized Identifier texture(String hash) {
        LocalModel localModel = localByHash(hash);
        byte[] png = localModel != null ? localModel.png : null;
        if (png == null) {
            ModelBundle bundle = remote.get(hash);
            if (bundle == null) {
                return null;
            }
            png = bundle.png;
        }
        return dev.playermodel.runtime.TextureCache.ensure(hash, png);
    }

    public synchronized void saveCache(ModelBundle bundle) {
        try {
            Files.createDirectories(cacheDir());
            Path file = cacheDir().resolve(bundle.hash + ".pmb");
            if (!Files.isRegularFile(file)) {
                Files.write(file, bundle.bytes);
            }
            remote.putIfAbsent(bundle.hash, bundle);
            prepared.putIfAbsent(bundle.hash, new PreparedModel(bundle.model));
        } catch (IOException e) {
            PlayerModelMod.LOG.warn("Failed to cache remote model {}", bundle.hash, e);
        }
    }

    public synchronized void addRemote(ModelBundle bundle) {
        remote.putIfAbsent(bundle.hash, bundle);
    }

    public static String sanitize(String name) {
        String base = name == null || name.isBlank() ? "model" : name.trim();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < base.length() && sb.length() < 48; i++) {
            char c = Character.toLowerCase(base.charAt(i));
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                sb.append(c);
            } else if (c == ' ' || c == '.') {
                sb.append('_');
            }
        }
        String result = sb.toString().replaceAll("_+", "_");
        while (result.startsWith("_") || result.endsWith("_")) {
            result = result.startsWith("_") ? result.substring(1) : result.substring(0, result.length() - 1);
        }
        return result.isBlank() ? "model" : result;
    }

    public static final class Meta {
        public String displayName = "";
        public String textureMode = "model";
        public String pivotMode = "model";
        public boolean customAnchors;
        public PhysicsConfig physics;
        public boolean doubleLayer = true;
        public boolean slim;
        public java.util.Map<String, AnchorMeta> anchors = new java.util.LinkedHashMap<>();
        public int textureWidth;
        public int textureHeight;
    }

    public static final class AnchorMeta {
        public Float x;
        public Float y;
        public Float z;
        public float scale;
    }

    private static List<dev.playermodel.model.Anchor> applyAnchorOverrides(
            List<dev.playermodel.model.Anchor> source,
            java.util.Map<String, AnchorMeta> overrides) {
        java.util.Map<String, dev.playermodel.model.Anchor> result = new java.util.LinkedHashMap<>();
        if (source != null) {
            for (dev.playermodel.model.Anchor anchor : source) {
                result.put(anchor.name().toLowerCase(java.util.Locale.ROOT), anchor);
            }
        }
        overrides.forEach((name, value) -> {
            if (value == null) {
                return;
            }
            String key = name.toLowerCase(java.util.Locale.ROOT);
            dev.playermodel.model.Anchor old = result.get(key);
            if (old == null) {
                return;
            }
            float x = value.x != null ? value.x : old.x();
            float y = value.y != null ? value.y : old.y();
            float z = value.z != null ? value.z : old.z();
            float scale = value.scale > 0 ? value.scale : old.scale();
            result.put(key, new dev.playermodel.model.Anchor(old.name(), old.bone(), x, y, z, scale));
        });
        return List.copyOf(result.values());
    }
}
