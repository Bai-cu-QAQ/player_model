package dev.playermodel.jpm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Parser for EMF's .jem model descriptor; referenced .jpm files are parsed recursively. */
public final class JemParser {
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_MODELS = 64;

    private JemParser() {
    }

    public static JemModel parse(Path file) throws JpmException {
        try {
            long size = Files.size(file);
            if (size <= 0 || size > MAX_BYTES) throw new JpmException("JEM 文件大小不合法: " + size);
            JsonElement element = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!element.isJsonObject()) throw new JpmException("JEM 根节点不是对象");
            JsonObject root = element.getAsJsonObject();
            int[] texture = ints(root, "textureSize", new int[]{64, 64});
            JsonArray models = array(root, "models");
            if (models == null) throw new JpmException("JEM 缺少 models");
            if (models.size() > MAX_MODELS) throw new JpmException("JEM 模型数量过多");
            List<JemPart> result = new ArrayList<>();
            for (int i = 0; i < models.size(); i++) {
                JsonElement item = models.get(i);
                if (!item.isJsonObject()) continue;
                JsonObject object = item.getAsJsonObject();
                String part = string(object, "part", "");
                String id = string(object, "id", part);
                String model = string(object, "model", null);
                JpmModel jpm = null;
                if (model != null && !model.isBlank()) {
                    Path relative = file.getParent() == null ? Path.of(model) : file.getParent().resolve(model);
                    relative = relative.normalize();
                    if (!relative.startsWith(file.getParent() == null ? relative : file.getParent().normalize())) {
                        throw new JpmException("JEM 引用了目录外模型: " + model);
                    }
                    if (!Files.isRegularFile(relative)) {
                        throw new JpmException("JEM 引用的 JPM 不存在: " + model);
                    }
                    jpm = JpmParser.parse(relative);
                }
                result.add(new JemPart(part, id, model, jpm));
            }
            return new JemModel(texture[0], texture[1], result);
        } catch (JpmException e) {
            throw e;
        } catch (JsonSyntaxException e) {
            throw new JpmException("JEM JSON 语法错误: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new JpmException("读取 JEM 失败: " + e.getMessage(), e);
        }
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

    private static int[] ints(JsonObject object, String key, int[] fallback) {
        JsonArray array = array(object, key);
        if (array == null || array.size() != 2) return fallback;
        try {
            return new int[]{array.get(0).getAsInt(), array.get(1).getAsInt()};
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
