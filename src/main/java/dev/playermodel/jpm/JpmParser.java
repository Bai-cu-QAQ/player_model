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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Full parser for OptiFine/EMF JPM player models. */
public final class JpmParser {
    private static final int MAX_BYTES = 32 * 1024 * 1024;
    private static final int MAX_NODES = 8192;
    private static final int MAX_BOXES = 8192;
    private static final int MAX_ANIMATIONS = 256;
    private static final int MAX_DEPTH = 64;

    private static final Map<String, FaceDir> UV_KEYS = Map.ofEntries(
            Map.entry("uvnorth", FaceDir.NORTH),
            Map.entry("uvsouth", FaceDir.SOUTH),
            Map.entry("uveast", FaceDir.EAST),
            Map.entry("uvwest", FaceDir.WEST),
            Map.entry("uvup", FaceDir.UP),
            Map.entry("uvdown", FaceDir.DOWN),
            Map.entry("uvfront", FaceDir.NORTH),
            Map.entry("uvback", FaceDir.SOUTH),
            Map.entry("uvleft", FaceDir.WEST),
            Map.entry("uvright", FaceDir.EAST)
    );

    private final Path source;
    private int nodes;
    private int boxes;

    private JpmParser(Path source) {
        this.source = source;
    }

    public static JpmModel parse(Path file) throws JpmException {
        try {
            long size = Files.size(file);
            if (size <= 0 || size > MAX_BYTES) {
                throw new JpmException("JPM 文件大小不合法: " + size);
            }
            return parseText(Files.readString(file, StandardCharsets.UTF_8));
        } catch (JpmException e) {
            throw e;
        } catch (IOException e) {
            throw new JpmException("读取 JPM 失败: " + e.getMessage(), e);
        }
    }

    public static JpmModel parseText(String text) throws JpmException {
        if (text == null || text.isBlank()) throw new JpmException("JPM 内容为空");
        try {
            JsonElement element = JsonParser.parseString(text);
            if (!element.isJsonObject()) throw new JpmException("JPM 根节点不是对象");
            return new JpmParser(null).parseRoot(element.getAsJsonObject());
        } catch (JpmException e) {
            throw e;
        } catch (JsonSyntaxException e) {
            throw new JpmException("JPM JSON 语法错误: " + e.getMessage(), e);
        }
    }

    private JpmModel parseRoot(JsonObject root) throws JpmException {
        String id = string(root, "id", "");
        int[] texture = ints(root, "textureSize", 2, new int[]{64, 64});
        if (texture[0] <= 0 || texture[1] <= 0) {
            throw new JpmException("JPM textureSize 无效");
        }
        JsonArray children = array(root, "submodels");
        if (children == null) children = array(root, "models");
        List<JpmPart> parts = new ArrayList<>();
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                if (!children.get(i).isJsonObject()) continue;
                parts.add(parsePart(children.get(i).getAsJsonObject(), 0, "submodels[" + i + "]"));
            }
        }
        List<JpmAnimation> animations = parseAnimations(array(root, "animations"));
        if (parts.isEmpty()) {
            throw new JpmException("JPM 没有模型部件");
        }
        return new JpmModel(id, texture[0], texture[1], parts, animations);
    }

    private List<JpmAnimation> parseAnimations(JsonArray array) throws JpmException {
        List<JpmAnimation> result = new ArrayList<>();
        if (array == null) return result;
        if (array.size() > MAX_ANIMATIONS) {
            throw new JpmException("JPM 动画数量过多: " + array.size());
        }
        for (int i = 0; i < array.size(); i++) {
            if (!array.get(i).isJsonObject()) continue;
            JsonObject source = array.get(i).getAsJsonObject();
            Map<String, JpmExpression> expressions = new LinkedHashMap<>();
            for (String key : source.keySet()) {
                JsonElement value = source.get(key);
                JpmExpression expression;
                try {
                    if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                        expression = JpmExpressionParser.parse(value.getAsString());
                    } else {
                        double number = value.getAsDouble();
                        expression = context -> number;
                    }
                } catch (JpmException e) {
                    throw new JpmException("animations[" + i + "]." + key + ": " + e.getMessage(), e);
                }
                expressions.put(key, expression);
            }
            result.add(new JpmAnimation(expressions));
        }
        return result;
    }

    private JpmPart parsePart(JsonObject object, int depth, String path) throws JpmException {
        if (depth > MAX_DEPTH) throw new JpmException("JPM 嵌套过深: " + path);
        if (++nodes > MAX_NODES) throw new JpmException("JPM 部件数量过多");
        String id = string(object, "id", "part" + nodes);
        float[] translate = floats(object, "translate", 3, new float[]{0, 0, 0});
        String invertAxis = string(object, "invertAxis", "");
        String mirrorTexture = string(object, "mirrorTexture", "");
        List<JpmBox> partBoxes = new ArrayList<>();
        JsonArray boxArray = array(object, "boxes");
        if (boxArray != null) {
            for (int i = 0; i < boxArray.size(); i++) {
                if (!boxArray.get(i).isJsonObject()) continue;
                if (++boxes > MAX_BOXES) throw new JpmException("JPM 盒子数量过多");
                try {
                    partBoxes.add(parseBox(boxArray.get(i).getAsJsonObject(), invertAxis,
                            mirrorTexture, path + ".boxes[" + i + "]"));
                } catch (JpmException e) {
                    throw new JpmException(path + ".boxes[" + i + "]: " + e.getMessage(), e);
                }
            }
        }
        List<JpmPart> children = new ArrayList<>();
        JsonArray childArray = array(object, "submodels");
        if (childArray != null) {
            for (int i = 0; i < childArray.size(); i++) {
                if (childArray.get(i).isJsonObject()) {
                    children.add(parsePart(childArray.get(i).getAsJsonObject(), depth + 1,
                            path + ".submodels[" + i + "]"));
                }
            }
        }
        return new JpmPart(id, translate, invertAxis, mirrorTexture, partBoxes, children);
    }

    private JpmBox parseBox(JsonObject object, String invertAxis, String mirrorTexture, String path)
            throws JpmException {
        float[] coordinates = floats(object, "coordinates", 6, null);
        if (coordinates == null || coordinates.length != 6) {
            throw new JpmException("盒子缺少 coordinates[6]");
        }
        applyInvert(coordinates, invertAxis);
        Map<FaceDir, float[]> uvs = new EnumMap<>(FaceDir.class);
        for (String key : object.keySet()) {
            FaceDir dir = UV_KEYS.get(key.toLowerCase(Locale.ROOT));
            if (dir == null || uvs.containsKey(dir)) continue;
            float[] uv = floats(object, key, 4, null);
            if (uv == null) continue;
            if (mirrorTexture.contains("u")) {
                float tmp = uv[0]; uv[0] = uv[2]; uv[2] = tmp;
            }
            if (mirrorTexture.contains("v")) {
                float tmp = uv[1]; uv[1] = uv[3]; uv[3] = tmp;
            }
            uvs.put(dir, uv);
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
        return new JpmBox(coordinates, uvs, sizeAdd, sizeAddX, sizeAddY, sizeAddZ);
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

    private static int[] ints(JsonObject object, String key, int length, int[] fallback) {
        float[] values = floats(object, key, length, null);
        if (values == null) return fallback;
        int[] result = new int[length];
        for (int i = 0; i < length; i++) result[i] = Math.round(values[i]);
        return result;
    }
}
