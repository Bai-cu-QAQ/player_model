package dev.playermodel.jpm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import dev.playermodel.model.FaceDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Parser for EMF's .jem model descriptor; referenced .jpm files are parsed recursively. */
public final class JemParser {
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_MODELS = 64;
    private static final int MAX_NODES = 8192;
    private static final int MAX_BOXES = 8192;
    private static final int MAX_DEPTH = 64;

    private static final Map<String, FaceDir> UV_KEYS = Map.ofEntries(
            Map.entry("uvnorth", FaceDir.NORTH),
            Map.entry("uvsouth", FaceDir.SOUTH),
            Map.entry("uveast", FaceDir.EAST),
            Map.entry("uvwest", FaceDir.WEST),
            Map.entry("uvup", FaceDir.UP),
            Map.entry("uvdown", FaceDir.DOWN),
            // EMF legacy names: Front->North, Back->West, Left->North, Right->East.
            Map.entry("uvfront", FaceDir.NORTH),
            Map.entry("uvback", FaceDir.WEST),
            Map.entry("uvleft", FaceDir.NORTH),
            Map.entry("uvright", FaceDir.EAST)
    );

    private JemParser() {
    }

    public static JemModel parse(Path file) throws JpmException {
        try {
            long size = Files.size(file);
            if (size <= 0 || size > MAX_BYTES) throw new JpmException("JEM 文件大小不合法: " + size);
            return parse(Files.readString(file, StandardCharsets.UTF_8), file.getParent());
        } catch (JpmException e) {
            throw e;
        } catch (IOException e) {
            throw new JpmException("读取 JEM 失败: " + e.getMessage(), e);
        }
    }

    /** Parses a JEM document whose referenced JPM paths resolve against {@code baseDir}. */
    public static JemModel parse(String text, Path baseDir) throws JpmException {
        try {
            JsonElement element = JsonParser.parseString(text);
            if (!element.isJsonObject()) throw new JpmException("JEM 根节点不是对象");
            JsonObject root = element.getAsJsonObject();
            int[] textureSize = ints(root, "textureSize", new int[]{64, 64});
            if (textureSize[0] <= 0 || textureSize[1] <= 0) throw new JpmException("JEM textureSize 无效");
            String rootTexture = string(root, "texture", "");
            JsonArray rootAnimations = array(root, "animations");
            JsonArray models = array(root, "models");
            if (models == null) throw new JpmException("JEM 缺少 models");
            if (models.size() > MAX_MODELS) throw new JpmException("JEM 模型数量过多");
            Limits limits = new Limits();
            List<JemPart> result = new ArrayList<>();
            for (int i = 0; i < models.size(); i++) {
                JsonElement item = models.get(i);
                if (!item.isJsonObject()) continue;
                JsonObject object = item.getAsJsonObject();
                String part = string(object, "part", "");
                String id = string(object, "id", part);
                String model = string(object, "model", null);
                boolean attach = bool(object, "attach", false);
                String texture = string(object, "texture", rootTexture);
                int[] entryTexture = ints(object, "textureSize", textureSize);
                JpmModel jpm = null;
                if (model != null && !model.isBlank()) {
                    jpm = parseReferenced(baseDir, model);
                }
                JpmModel inline = parseInline(object, id, texture, entryTexture, limits,
                        "models[" + i + "]", array(object, "animations") == null
                                ? rootAnimations : array(object, "animations"));
                result.add(new JemPart(part, id, model, jpm, attach, texture,
                        entryTexture[0], entryTexture[1], inline));
            }
            return new JemModel(textureSize[0], textureSize[1], result);
        } catch (JpmException e) {
            throw e;
        } catch (JsonSyntaxException e) {
            throw new JpmException("JEM JSON 语法错误: " + e.getMessage(), e);
        }
    }

    private static JpmModel parseReferenced(Path baseDir, String model) throws JpmException {
        Path parent = baseDir == null ? Path.of("").toAbsolutePath().normalize()
                : baseDir.toAbsolutePath().normalize();
        Path relative = parent.resolve(model).normalize();
        if (!relative.startsWith(parent)) {
            throw new JpmException("JEM 引用了目录外模型: " + model);
        }
        if (!Files.isRegularFile(relative)) {
            throw new JpmException("JEM 引用的 JPM 不存在: " + model);
        }
        return JpmParser.parse(relative);
    }

    private static JpmModel parseInline(JsonObject entry, String id, String texture, int[] textureSize,
                                        Limits limits, String path, JsonArray animations) throws JpmException {
        if (!entry.has("boxes") && !entry.has("submodels")) return null;
        JpmPart root = parsePart(entry, id, 0, path, limits);
        return new JpmModel(id, textureSize[0], textureSize[1], texture, List.of(root),
                animations == null ? List.of() : JpmParser.parseAnimations(animations));
    }

    private static JpmPart parsePart(JsonObject object, String forcedId, int depth, String path,
                                     Limits limits) throws JpmException {
        if (depth > MAX_DEPTH) throw new JpmException("JEM 嵌套过深: " + path);
        if (++limits.nodes > MAX_NODES) throw new JpmException("JEM 部件数量过多");
        String id = forcedId != null ? forcedId : string(object, "id", "part" + limits.nodes);
        float[] translate = floats(object, "translate", 3, new float[]{0, 0, 0});
        float[] rotate = floats(object, "rotate", 3, new float[]{0, 0, 0});
        float scale = number(object, "scale", 1.0F);
        String invertAxis = string(object, "invertAxis", "");
        String mirrorTexture = string(object, "mirrorTexture", "");
        invertPart(translate, rotate, invertAxis);
        List<JpmBox> partBoxes = new ArrayList<>();
        JsonArray boxArray = array(object, "boxes");
        if (boxArray != null) {
            for (int i = 0; i < boxArray.size(); i++) {
                if (!boxArray.get(i).isJsonObject()) continue;
                if (++limits.boxes > MAX_BOXES) throw new JpmException("JEM 盒子数量过多");
                try {
                    partBoxes.add(parseBox(boxArray.get(i).getAsJsonObject(), invertAxis,
                            mirrorTexture));
                } catch (JpmException e) {
                    throw new JpmException(path + ".boxes[" + i + "]: " + e.getMessage(), e);
                }
            }
        }
        List<JpmPart> children = new ArrayList<>();
        JsonArray childArray = array(object, "submodels");
        if (childArray != null) {
            for (int i = 0; i < childArray.size(); i++) {
                if (!childArray.get(i).isJsonObject()) continue;
                children.add(parsePart(childArray.get(i).getAsJsonObject(), null, depth + 1,
                        path + ".submodels[" + i + "]", limits));
            }
        }
        return new JpmPart(id, translate, rotate, scale, partBoxes, children);
    }

    private static JpmBox parseBox(JsonObject object, String invertAxis, String mirrorTexture)
            throws JpmException {
        float[] coordinates = floats(object, "coordinates", 6, null);
        if (coordinates == null || coordinates.length != 6) {
            throw new JpmException("盒子缺少 coordinates[6]");
        }
        applyInvert(coordinates, invertAxis);
        float[] textureOffset = floats(object, "textureOffset", 2, null);
        if (object.has("textureOffset") && textureOffset == null) {
            throw new JpmException("textureOffset 必须是长度 2 的数组");
        }
        Map<FaceDir, float[]> uvs = new EnumMap<>(FaceDir.class);
        for (String key : object.keySet()) {
            FaceDir dir = UV_KEYS.get(key.toLowerCase(Locale.ROOT));
            if (dir == null) continue;
            float[] uv = floats(object, key, 4, null);
            if (uv != null) uvs.put(dir, uv);
        }
        float sizeAdd = number(object, "sizeAdd", 0);
        float sizeAddX = number(object, "sizeAddX", 0);
        float sizeAddY = number(object, "sizeAddY", 0);
        float sizeAddZ = number(object, "sizeAddZ", 0);
        float[] sizes = floats(object, "sizesAdd", 3, null);
        if (sizes != null) {
            sizeAddX = sizes[0];
            sizeAddY = sizes[1];
            sizeAddZ = sizes[2];
        }
        return new JpmBox(coordinates, uvs, textureOffset, mirrorTexture,
                sizeAdd, sizeAddX, sizeAddY, sizeAddZ);
    }

    private static void invertPart(float[] translate, float[] rotate, String axis) {
        if (axis == null) return;
        String lower = axis.toLowerCase(Locale.ROOT);
        for (int i = 0; i < 3; i++) {
            char c = (char) ('x' + i);
            if (lower.indexOf(c) >= 0) {
                translate[i] = -translate[i];
                rotate[i] = -rotate[i];
            }
            rotate[i] = (float) Math.toRadians(rotate[i]);
        }
    }

    private static void applyInvert(float[] c, String axis) {
        if (axis == null) return;
        String lower = axis.toLowerCase(Locale.ROOT);
        if (lower.contains("x")) c[0] = -c[0] - c[3];
        if (lower.contains("y")) c[1] = -c[1] - c[4];
        if (lower.contains("z")) c[2] = -c[2] - c[5];
    }

    private static JsonArray array(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonArray()) return null;
        return object.getAsJsonArray(key);
    }

    private static String string(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return fallback;
        try {
            return object.get(key).getAsString();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return fallback;
        try {
            return object.get(key).getAsBoolean();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static float number(JsonObject object, String key, float fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return fallback;
        try {
            return object.get(key).getAsFloat();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static float[] floats(JsonObject object, String key, int length, float[] fallback) {
        if (object == null || !object.has(key) || !object.get(key).isJsonArray()) return fallback;
        JsonArray array = object.getAsJsonArray(key);
        if (array.size() != length) return fallback;
        float[] result = new float[length];
        for (int i = 0; i < length; i++) {
            try {
                result[i] = array.get(i).getAsFloat();
            } catch (RuntimeException e) {
                return fallback;
            }
        }
        return result;
    }

    private static int[] ints(JsonObject object, String key, int[] fallback) {
        float[] values = floats(object, key, 2, null);
        if (values == null) return fallback;
        return new int[]{Math.round(values[0]), Math.round(values[1])};
    }

    private static final class Limits {
        int nodes;
        int boxes;
    }
}
