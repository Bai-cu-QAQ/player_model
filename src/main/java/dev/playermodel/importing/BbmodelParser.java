package dev.playermodel.importing;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.playermodel.model.BoneKind;
import dev.playermodel.model.Anchor;
import dev.playermodel.model.FaceDir;
import dev.playermodel.model.RawBone;
import dev.playermodel.model.RawElement;
import dev.playermodel.model.RawFace;
import dev.playermodel.model.RawGroup;
import dev.playermodel.model.RawModel;
import dev.playermodel.model.RawTexture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses a Blockbench .bbmodel project (cube models only) into a {@link RawModel} whose bones are
 * mapped onto the fourteen vanilla player bones and whose coordinates are in vanilla model space.
 */
public final class BbmodelParser {
    private static final int MAX_JSON_BYTES = 32 * 1024 * 1024;
    private static final int MAX_ELEMENTS = 4096;
    private static final int MAX_TEXTURES = 16;

    private final Path sourceFile;
    private final JsonObject root;
    private final List<String> warnings = new ArrayList<>();
    private final Map<String, RawElement> elementsByUuid = new HashMap<>();
    private final Map<String, JsonObject> groupDataByUuid = new HashMap<>();
    private final Set<String> referenced = new HashSet<>();
    private final List<RawGroup> rootGroups = new ArrayList<>();
    private final Map<BoneKind, RawBone> bones = new EnumMap<>(BoneKind.class);

    private BbmodelParser(Path sourceFile, JsonObject root) {
        this.sourceFile = sourceFile;
        this.root = root;
    }

    public static RawModel parse(Path file, String displayName) throws ImportException {
        try {
            long size = Files.size(file);
            if (size <= 0 || size > MAX_JSON_BYTES) {
                throw new ImportException("模型文件大小不合法: " + size + " 字节");
            }
            byte[] bytes = Files.readAllBytes(file);
            JsonElement parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                throw new ImportException("不是有效的 Blockbench .bbmodel 文件");
            }
            BbmodelParser parser = new BbmodelParser(file, parsed.getAsJsonObject());
            return parser.parseInternal(displayName);
        } catch (ImportException e) {
            throw e;
        } catch (IOException e) {
            throw new ImportException("读取模型文件失败: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new ImportException("解析模型失败: " + e.getMessage(), e);
        }
    }

    private RawModel parseInternal(String displayName) throws ImportException {
        JsonObject meta = object(root, "meta");
        String format = string(meta, "model_format", string(meta, "format", "unknown"));

        JsonArray elementArray = array(root, "elements");
        if (elementArray == null || elementArray.isEmpty()) {
            throw new ImportException("模型里没有方块（elements 为空）");
        }
        if (elementArray.size() > MAX_ELEMENTS) {
            throw new ImportException("方块数量过多: " + elementArray.size());
        }

        for (JsonElement element : elementArray) {
            if (!element.isJsonObject()) {
                continue;
            }
            RawElement parsed = parseElement(element.getAsJsonObject());
            if (parsed != null) {
                elementsByUuid.put(parsed.uuid, parsed);
            }
        }
        if (elementsByUuid.isEmpty()) {
            throw new ImportException("模型里没有可用的方块（可能全部是 mesh）");
        }

        JsonArray outliner = array(root, "outliner");
        JsonArray groups = array(root, "groups");
        if (groups != null) {
            for (JsonElement group : groups) {
                if (group.isJsonObject()) {
                    JsonObject object = group.getAsJsonObject();
                    String uuid = string(object, "uuid", null);
                    if (uuid != null) {
                        groupDataByUuid.put(uuid, object);
                    }
                }
            }
        }
        RawGroup rootGroup = new RawGroup();
        rootGroup.name = "root";
        if (outliner != null) {
            parseOutliner(outliner, rootGroup);
        }
        // Elements that are not mentioned in the outliner still belong to the model.
        for (Map.Entry<String, RawElement> entry : elementsByUuid.entrySet()) {
            if (!referenced.contains(entry.getKey())) {
                rootGroup.elements.add(entry.getValue());
            }
        }
        rootGroups.add(rootGroup);

        RawTexture texture = pickTexture();
        int[] resolution = resolution();
        int uvW = texture.uvWidth() > 0 ? texture.uvWidth() : resolution[0];
        int uvH = texture.uvHeight() > 0 ? texture.uvHeight() : resolution[1];

        ModelSpace space = detectSpace(format);
        warnings.add("坐标空间: " + (space == ModelSpace.BLOCKBENCH ? "Blockbench (Y-up, 脚底 y=0)" : "原版 (Y-down, 头部 y=0)"));
        convertSpace(rootGroup, space);

        List<Anchor> anchors = new ArrayList<>();
        collectAnchors(rootGroup, anchors);

        RawModel model = new RawModel();
        model.displayName = displayName;
        model.sourceFormat = format;
        model.texture = texture;
        model.textures.addAll(textures());
        model.textureWidth = uvW;
        model.textureHeight = uvH;
        model.selectedTextureIndex = selectedTextureIndex;
        model.textureCount = textures().size();
        model.warnings.addAll(warnings);
        model.anchors.addAll(anchors);

        assignBones(rootGroup, null);
        validateRequiredBones();
        model.bones.addAll(bones.values());
        RawBone slimArm = model.bone(BoneKind.RIGHT_ARM);
        if (slimArm != null) {
            for (RawElement element : slimArm.elements) {
                float width = Math.abs(element.to[0] - element.from[0]);
                if (width > 0.1F && width <= 3.25F) {
                    model.slim = true;
                    break;
                }
            }
        }
        if (model.bones.isEmpty()) {
            throw new ImportException("没有找到任何原版玩家骨骼");
        }
        return model;
    }

    private int selectedTextureIndex;

    private RawTexture pickTexture() throws ImportException {
        List<RawTexture> textures = textures();
        if (textures.isEmpty()) {
            throw new ImportException("模型没有内置贴图（textures 为空）");
        }
        int[] counts = new int[textures.size()];
        for (RawElement element : elementsByUuid.values()) {
            for (RawFace face : element.faces.values()) {
                if (face.textureIndex() >= 0 && face.textureIndex() < counts.length) {
                    counts[face.textureIndex()]++;
                }
            }
        }
        int best = 0;
        for (int i = 1; i < counts.length; i++) {
            if (counts[i] > counts[best]) {
                best = i;
            }
        }
        if (counts[best] == 0) {
            best = 0;
        }
        selectedTextureIndex = best;
        if (textures.size() > 1) {
            int used = 0;
            for (int c : counts) {
                if (c > 0) {
                    used++;
                }
            }
            if (used > 1) {
                warnings.add("模型使用了 " + used + " 张贴图；已自动合并为一张图集");
            }
        }
        return textures.get(best);
    }

    private List<RawTexture> cachedTextures;

    private List<RawTexture> textures() throws ImportException {
        if (cachedTextures != null) {
            return cachedTextures;
        }
        List<RawTexture> result = new ArrayList<>();
        JsonArray array = array(root, "textures");
        if (array == null) {
            return result;
        }
        if (array.size() > MAX_TEXTURES) {
            throw new ImportException("贴图数量过多: " + array.size());
        }
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject obj = element.getAsJsonObject();
            String source = string(obj, "source", null);
            if (source == null || source.isBlank()) {
                continue;
            }
            byte[] png;
            if (source.startsWith("data:")) {
                png = PngUtils.decodeDataUri(source);
            } else {
                Path relative = sourceFile.getParent() == null ? Path.of(source) : sourceFile.getParent().resolve(source);
                if (!Files.isRegularFile(relative)) {
                    throw new ImportException("找不到贴图文件: " + source);
                }
                try {
                    png = Files.readAllBytes(relative);
                } catch (IOException e) {
                    throw new ImportException("读取贴图文件失败: " + source, e);
                }
            }
            int[] size = PngUtils.readSize(png);
            int uvW = number(obj, "uv_width", size[0]);
            int uvH = number(obj, "uv_height", size[1]);
            result.add(new RawTexture(string(obj, "name", "texture"), png, size[0], size[1], uvW, uvH));
        }
        cachedTextures = result;
        return result;
    }

    private int[] resolution() {
        JsonObject resolution = object(root, "resolution");
        int width = number(resolution, "width", 16);
        int height = number(resolution, "height", 16);
        return new int[]{Math.max(1, width), Math.max(1, height)};
    }

    private RawElement parseElement(JsonObject obj) throws ImportException {
        String type = string(obj, "type", "");
        if ("mesh".equalsIgnoreCase(type) || obj.has("vertices")) {
            throw new ImportException("模型包含网格(mesh)方块；本 mod 只支持方块模型，请在 Blockbench 中删除 mesh 或转换为方块");
        }
        if (obj.has("visibility") && !obj.get("visibility").getAsBoolean()) {
            return null;
        }
        if (obj.has("export") && !obj.get("export").getAsBoolean()) {
            return null;
        }
        RawElement element = new RawElement();
        element.uuid = string(obj, "uuid", java.util.UUID.randomUUID().toString());
        element.name = string(obj, "name", "");
        element.from = floats(obj, "from", 3, null);
        element.to = floats(obj, "to", 3, null);
        if (element.from == null || element.to == null) {
            throw new ImportException("方块缺少 from/to 坐标: " + element.name);
        }
        element.origin = floats(obj, "origin", 3, new float[]{0, 0, 0});
        element.rotation = floats(obj, "rotation", 3, new float[]{0, 0, 0});
        for (float angle : element.rotation) {
            if (!Float.isFinite(angle)) {
                throw new ImportException("方块 '" + safeName(element) + "' 的旋转不是有限数值");
            }
        }
        element.inflate = number(obj, "inflate", 0.0F);
        element.mirrorUv = bool(obj, "mirror_uv", false);
        element.boxUv = bool(obj, "box_uv", false);
        element.uvOffset = floats(obj, "uv_offset", 2, new float[]{0, 0});
        element.faces = parseFaces(obj);
        return element;
    }

    private Map<FaceDir, RawFace> parseFaces(JsonObject obj) throws ImportException {
        Map<FaceDir, RawFace> faces = new EnumMap<>(FaceDir.class);
        JsonObject facesObj = object(obj, "faces");
        if (facesObj == null) {
            return faces;
        }
        for (String key : facesObj.keySet()) {
            FaceDir dir = FaceDir.byKey(key);
            if (dir == null) {
                continue;
            }
            JsonElement value = facesObj.get(key);
            if (!value.isJsonObject()) {
                continue;
            }
            JsonObject faceObj = value.getAsJsonObject();
            float[] uv = floats(faceObj, "uv", 4, null);
            if (uv == null) {
                continue;
            }
            int rotation = Math.floorMod(number(faceObj, "rotation", 0), 360);
            if (rotation % 90 != 0) {
                throw new ImportException("面 UV 旋转必须是 90 的倍数，发现: " + rotation);
            }
            int texture = number(faceObj, "texture", 0);
            faces.put(dir, new RawFace(uv[0], uv[1], uv[2], uv[3], rotation, texture));
        }
        return faces;
    }

    private void parseOutliner(JsonArray array, RawGroup parent) throws ImportException {
        for (JsonElement element : array) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                attachElement(parent, element.getAsString());
            } else if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                if (obj.has("children")) {
                    RawGroup group = parseGroup(obj);
                    if (group != null) {
                        parent.children.add(group);
                    }
                } else if (obj.has("from")) {
                    RawElement parsed = parseElement(obj);
                    if (parsed != null) {
                        elementsByUuid.put(parsed.uuid, parsed);
                        parent.elements.add(parsed);
                    }
                }
            }
        }
    }

    private RawGroup parseGroup(JsonObject node) throws ImportException {
        String uuid = string(node, "uuid", null);
        JsonObject data = uuid != null && groupDataByUuid.containsKey(uuid) ? groupDataByUuid.get(uuid) : node;
        if (bool(data, "visibility", true) == false || bool(node, "visibility", true) == false) {
            return null;
        }
        RawGroup group = new RawGroup();
        group.name = string(data, "name", string(node, "name", ""));
        group.origin = floats(data, "origin", 3, new float[]{0, 0, 0});
        group.rotation = floats(data, "rotation", 3, new float[]{0, 0, 0});
        if (nonzero(group.rotation)) {
            throw new ImportException("骨骼分组 '" + safeName(group) + "' 带有旋转；请先在 Blockbench 中把骨骼旋转归零（本 mod 保留原版骨骼动画，不支持静态骨骼旋转）");
        }
        if (data.has("scale")) {
            float[] scale = floats(data, "scale", 3, null);
            if (scale != null && (Math.abs(scale[0] - 1) > 1e-3 || Math.abs(scale[1] - 1) > 1e-3 || Math.abs(scale[2] - 1) > 1e-3)) {
                throw new ImportException("骨骼分组 '" + safeName(group) + "' 带有缩放；请在 Blockbench 中应用缩放（Ctrl+A）后再导入");
            }
        }
        group.scale = floats(data, "scale", 3, new float[]{1, 1, 1});
        group.anchorScale = number(data, "cem_scale", number(data, "anchor_scale", 0.0F));
        JsonArray children = array(node, "children");
        if (children != null) {
            parseOutliner(children, group);
        }
        return group;
    }

    private void attachElement(RawGroup group, String uuid) {
        RawElement element = elementsByUuid.get(uuid);
        if (element != null) {
            referenced.add(uuid);
            group.elements.add(element);
        }
    }

    private ModelSpace detectSpace(String format) {
        if (format.toLowerCase(java.util.Locale.ROOT).contains("bedrock")) {
            return ModelSpace.BLOCKBENCH;
        }
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (RawElement element : elementsByUuid.values()) {
            minY = Math.min(minY, Math.min(element.from[1], element.to[1]));
            maxY = Math.max(maxY, Math.max(element.from[1], element.to[1]));
        }
        float headY = Float.NaN;
        float bodyY = Float.NaN;
        for (RawGroup group : rootGroups) {
            headY = findGroupOriginY(group, "head", headY);
            bodyY = findGroupOriginY(group, "body", bodyY);
        }
        if (!Float.isNaN(headY) && headY >= 12.0F) {
            return ModelSpace.BLOCKBENCH;
        }
        if (!Float.isNaN(bodyY) && bodyY >= 8.0F && minY > -2.0F) {
            return ModelSpace.BLOCKBENCH;
        }
        if (maxY > 28.0F) {
            return ModelSpace.BLOCKBENCH;
        }
        if (minY > -2.0F && maxY > 12.0F && !Float.isNaN(headY) && headY > 6.0F) {
            return ModelSpace.BLOCKBENCH;
        }
        return ModelSpace.VANILLA;
    }

    private float findGroupOriginY(RawGroup group, String name, float current) {
        BoneKind kind = BoneKind.byName(group.name);
        if (kind != null) {
            if (kind == BoneKind.HEAD && "head".equals(name)) {
                current = group.origin[1];
            } else if (kind == BoneKind.BODY && "body".equals(name)) {
                current = group.origin[1];
            }
        }
        for (RawGroup child : group.children) {
            current = findGroupOriginY(child, name, current);
        }
        return current;
    }

    private void convertSpace(RawGroup group, ModelSpace space) {
        group.origin[1] = space.y(group.origin[1]);
        for (RawElement element : group.elements) {
            element.from[1] = space.y(element.from[1]);
            element.to[1] = space.y(element.to[1]);
            element.origin[1] = space.y(element.origin[1]);
            if (space == ModelSpace.BLOCKBENCH) {
                // Reflecting the Y axis flips the handedness of X/Z rotations.
                element.rotation[0] = -element.rotation[0];
                element.rotation[2] = -element.rotation[2];
            }
        }
        for (RawGroup child : group.children) {
            convertSpace(child, space);
        }
    }

    private void assignBones(RawGroup group, BoneKind current) throws ImportException {
        if (group.anchor) {
            return;
        }
        BoneKind next = current;
        BoneKind mapped = BoneKind.byName(group.name);
        if (mapped != null) {
            next = mapped;
            RawBone bone = bones.get(mapped);
            if (bone == null) {
                bones.put(mapped, new RawBone(mapped, group.origin.clone()));
            } else if (group.elements.size() > 0) {
                warnings.add("骨骼 '" + mapped.modelName() + "' 出现多次，方块已合并");
            }
        } else if (BoneKind.isContainerName(group.name)) {
            // container: keep current target
        } else if (current == null) {
            if (!group.elements.isEmpty()) {
                throw new ImportException("方块分组 '" + safeName(group) + "' 不属于任何原版玩家骨骼；请把方块放进 head/body/right_arm 等 14 个骨骼分组内");
            }
            if (!group.name.isBlank()) {
                warnings.add("忽略未知容器分组: " + group.name);
            }
        } else if (!group.name.isBlank()) {
            warnings.add("额外分组 '" + group.name + "' 已合并到骨骼 '" + current.modelName() + "'（只跟随父骨骼）");
        }

        if (next != null) {
            for (RawElement element : group.elements) {
                bones.get(next).elements.add(element);
            }
        } else if (!group.elements.isEmpty()) {
            throw new ImportException("模型根节点存在不属于任何骨骼的方块");
        }

        for (RawGroup child : group.children) {
            assignBones(child, next);
        }
    }

    private void collectAnchors(RawGroup group, List<Anchor> anchors) {
        Anchor anchor = anchorFor(group);
        if (anchor != null) {
            group.anchor = true;
            anchors.add(anchor);
            return;
        }
        for (RawGroup child : group.children) {
            collectAnchors(child, anchors);
        }
    }

    private Anchor anchorFor(RawGroup group) {
        String name = group.name == null ? "" : group.name.toLowerCase(java.util.Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        if (!name.startsWith("anchor")) {
            return null;
        }
        String key = name.substring("anchor".length()).replaceFirst("^_+", "");
        String canonical;
        BoneKind bone;
        switch (key) {
            case "helmet", "head", "hat" -> {
                canonical = Anchor.HELMET;
                bone = BoneKind.HEAD;
            }
            case "chest", "chestplate", "body" -> {
                canonical = Anchor.CHEST;
                bone = BoneKind.BODY;
            }
            case "legs", "leggings", "pants" -> {
                canonical = Anchor.LEGS;
                bone = BoneKind.BODY;
            }
            case "boots", "feet", "shoes" -> {
                canonical = Anchor.BOOTS;
                bone = BoneKind.RIGHT_LEG;
            }
            case "right_hand", "hand_right", "righthand" -> {
                canonical = Anchor.RIGHT_HAND;
                bone = BoneKind.RIGHT_ARM;
            }
            case "left_hand", "hand_left", "lefthand" -> {
                canonical = Anchor.LEFT_HAND;
                bone = BoneKind.LEFT_ARM;
            }
            case "cape", "cloak" -> {
                canonical = Anchor.CAPE;
                bone = BoneKind.CLOAK;
            }
            default -> {
                return null;
            }
        }
        float scale = group.anchorScale;
        if (scale <= 0 && group.scale != null && group.scale.length >= 1
                && (Math.abs(group.scale[0] - 1.0F) > 1.0E-4F
                || Math.abs(group.scale[1] - 1.0F) > 1.0E-4F
                || Math.abs(group.scale[2] - 1.0F) > 1.0E-4F)) {
            scale = group.scale[0];
        }
        if (scale <= 0 && !group.elements.isEmpty()) {
            // Anchor markers are 4x4x4 model pixels by default; resizing the marker in Blockbench
            // changes the feature scale (size / 4).
            RawElement marker = group.elements.get(0);
            float sx = Math.abs(marker.to[0] - marker.from[0]);
            float sy = Math.abs(marker.to[1] - marker.from[1]);
            float sz = Math.abs(marker.to[2] - marker.from[2]);
            scale = Math.max(sx, Math.max(sy, sz)) / 4.0F;
        }
        return new Anchor(canonical, bone, group.origin[0], group.origin[1], group.origin[2], scale);
    }

    private void validateRequiredBones() throws ImportException {
        BoneKind[] required = {BoneKind.HEAD, BoneKind.BODY, BoneKind.RIGHT_ARM, BoneKind.LEFT_ARM,
                BoneKind.RIGHT_LEG, BoneKind.LEFT_LEG};
        List<String> missing = new ArrayList<>();
        for (BoneKind kind : required) {
            if (!bones.containsKey(kind)) {
                missing.add(kind.modelName());
            }
        }
        if (!missing.isEmpty()) {
            throw new ImportException("缺少玩家核心骨骼: " + String.join(", ", missing)
                    + "。请使用模板，并把方块放进这些命名的分组里");
        }
    }

    private static String safeName(RawElement element) {
        return element.name == null || element.name.isBlank() ? element.uuid : element.name;
    }

    private static String safeName(RawGroup group) {
        return group.name == null || group.name.isBlank() ? "(unnamed)" : group.name;
    }

    private static boolean nonzero(float[] values) {
        for (float v : values) {
            if (Math.abs(v) > 1e-3F) {
                return true;
            }
        }
        return false;
    }

    private static JsonObject object(JsonObject parent, String name) {
        if (parent == null || !parent.has(name) || !parent.get(name).isJsonObject()) {
            return null;
        }
        return parent.getAsJsonObject(name);
    }

    private static JsonArray array(JsonObject parent, String name) {
        if (parent == null || !parent.has(name) || !parent.get(name).isJsonArray()) {
            return null;
        }
        return parent.getAsJsonArray(name);
    }

    private static String string(JsonObject parent, String name, String fallback) {
        if (parent == null || !parent.has(name) || parent.get(name).isJsonNull()) {
            return fallback;
        }
        try {
            return parent.get(name).getAsString();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject parent, String name, boolean fallback) {
        if (parent == null || !parent.has(name) || parent.get(name).isJsonNull()) {
            return fallback;
        }
        try {
            return parent.get(name).getAsBoolean();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static int number(JsonObject parent, String name, int fallback) {
        if (parent == null || !parent.has(name) || parent.get(name).isJsonNull()) {
            return fallback;
        }
        try {
            return parent.get(name).getAsInt();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static float number(JsonObject parent, String name, float fallback) {
        if (parent == null || !parent.has(name) || parent.get(name).isJsonNull()) {
            return fallback;
        }
        try {
            return parent.get(name).getAsFloat();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static float[] floats(JsonObject parent, String name, int length, float[] fallback) {
        if (parent == null || !parent.has(name) || !parent.get(name).isJsonArray()) {
            return fallback;
        }
        JsonArray array = parent.getAsJsonArray(name);
        if (array.size() < length) {
            return fallback;
        }
        float[] result = new float[length];
        for (int i = 0; i < length; i++) {
            result[i] = array.get(i).getAsFloat();
        }
        return result;
    }
}
