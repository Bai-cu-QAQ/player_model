package dev.playermodel.runtime;

import dev.playermodel.jpm.JpmEvalContext;
import dev.playermodel.model.BoneKind;
import dev.playermodel.model.PhysicsConfig;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A standalone player model containing only the imported cube geometry. It is driven by copying the
 * animated transforms from the live (vanilla or EMF/CEM) player model every frame, so resource-pack
 * animations keep working while the visible geometry is the imported one.
 */
public final class CustomModel {
    private final PlayerEntityModel model;
    private final Map<BoneKind, ModelPart> parts = new EnumMap<>(BoneKind.class);
    private final Map<BoneKind, float[]> basePivots = new EnumMap<>(BoneKind.class);
    private final boolean modelPivots;
    private final List<PhysicsBoneRuntime> physicsBones = new ArrayList<>();
    private final Map<BoneKind, Quaternionf> staticRotations = new EnumMap<>(BoneKind.class);
    private final Map<ModelPart, ModelPart> parents = new IdentityHashMap<>();
    private final ModelPart rootPart;
    private final JpmFaceModel face;
    private final List<JpmFaceModel> jemBaseFaces;
    private final List<JemBody> jemBodies;

    private record JemBody(BoneKind kind, JpmFaceModel model, boolean attach) {
    }

    public record PhysicsBoneRuntime(String name, ModelPart part, ModelPart parent,
                                     BoneKind parentBone, String parentPhysics,
                                     float pivotX, float pivotY, float pivotZ,
                                     PhysicsConfig.Settings settings, Quaternionf staticRotation) {
    }

    public CustomModel(PreparedModel prepared) {
        TexturedModelData data = TexturedModelData.of(PlayerEntityModel.getTexturedModelData(
                Dilation.NONE, prepared.slim()), 64, 64);
        ModelPart root = data.createModel();
        this.rootPart = root;
        this.model = new PlayerEntityModel(root, prepared.slim());
        ModelPart[] array = ModelParts.of(model);
        buildParentMap(rootPart);
        for (BoneKind kind : BoneKind.VALUES) {
            parts.put(kind, array[kind.ordinal()]);
            basePivots.put(kind, restOrigin(array[kind.ordinal()], root));
        }
        this.modelPivots = prepared.usesModelPivots();
        for (BoneKind kind : BoneKind.VALUES) {
            staticRotations.put(kind, prepared.staticRotation(kind));
        }
        Map<BoneKind, List<ModelPart.Cuboid>> cubes = prepared.cuboids(modelPivots, basePivots);
        for (BoneKind kind : BoneKind.VALUES) {
            ModelPart part = array[kind.ordinal()];
            // Empty for layer bones unless the bbmodel itself defines them. There is no automatic
            // extra skin layer in this version.
            part.cuboids = cubes.getOrDefault(kind, List.of());
            float[] origin = localOrigin(prepared, kind);
            part.setOrigin(origin[0], origin[1], origin[2]);
            part.visible = true;
            part.hidden = false;
        }
        attachPhysics(prepared, array);
        this.jemBaseFaces = buildJemBaseFaces(prepared);
        this.jemBodies = buildJemBodies(prepared);
        this.face = prepared.face() == null ? null : new JpmFaceModel(prepared.face());
        ModelPart head = parts.get(BoneKind.HEAD);
        Map<String, ModelPart> children = new LinkedHashMap<>();
        if (head.children != null) children.putAll(head.children);
        for (int i = 0; i < jemBaseFaces.size(); i++) {
            children.put("pm_jem_base_" + i, jemBaseFaces.get(i).root());
        }
        if (face != null) {
            float[] headAbsolute = absoluteOrigin(prepared, BoneKind.HEAD);
            JpmAnchorTransform.apply(prepared, face.root(), headAbsolute);
            if (prepared.faceTexturePng() == null) {
                children.put("pm_face", face.root());
            }
        }
        head.children = children;
        if (!jemBaseFaces.isEmpty()) {
            head.cuboids = List.of();
            ModelPart hat = parts.get(BoneKind.HAT);
            if (hat != null) hat.cuboids = List.of();
        }
        for (JemBody body : jemBodies) {
            ModelPart target = parts.get(body.kind());
            if (target == null) continue;
            Map<String, ModelPart> bodyChildren = target.children == null
                    ? new LinkedHashMap<>() : new LinkedHashMap<>(target.children);
            bodyChildren.put("pm_jem_" + body.kind().name(), body.model().root());
            target.children = bodyChildren;
            if (!body.attach()) target.cuboids = List.of();
        }
        buildParentMap(rootPart);
    }

    private static List<JpmFaceModel> buildJemBaseFaces(PreparedModel prepared) {
        if (prepared == null || prepared.data() == null
                || prepared.data().jem == null || prepared.data().jem.isBlank()) {
            return List.of();
        }
        try {
            java.nio.file.Path base = prepared.data().jemDirectory == null
                    ? java.nio.file.Path.of("") : java.nio.file.Path.of(prepared.data().jemDirectory);
            var jem = dev.playermodel.jpm.JemParser.parse(prepared.data().jem, base);
            List<JpmFaceModel> result = new ArrayList<>();
            for (String part : new String[]{"head", "headwear"}) {
                var inline = jem.inlineForVanillaPart(part);
                if (inline == null) continue;
                result.add(new JpmFaceModel(inline));
            }
            return result;
        } catch (dev.playermodel.jpm.JpmException | RuntimeException e) {
            return List.of();
        }
    }

    private static List<JemBody> buildJemBodies(PreparedModel prepared) {
        if (prepared == null || prepared.data() == null
                || prepared.data().jem == null || prepared.data().jem.isBlank()
                || !"full".equalsIgnoreCase(prepared.data().jemMode)) {
            return List.of();
        }
        try {
            java.nio.file.Path base = prepared.data().jemDirectory == null
                    ? java.nio.file.Path.of("") : java.nio.file.Path.of(prepared.data().jemDirectory);
            var jem = dev.playermodel.jpm.JemParser.parse(prepared.data().jem, base);
            List<JemBody> result = new ArrayList<>();
            for (var entry : jem.attachments()) {
                BoneKind kind = BoneKind.byName(entry.part());
                if (kind == null || kind == BoneKind.HEAD || kind == BoneKind.HAT) continue;
                var inline = entry.inline();
                if (inline == null) continue;
                result.add(new JemBody(kind, new JpmFaceModel(inline), entry.attach()));
            }
            return result;
        } catch (dev.playermodel.jpm.JpmException | RuntimeException e) {
            return List.of();
        }
    }

    private float[] restOrigin(ModelPart part, ModelPart root) {
        float[] origin = new float[3];
        for (ModelPart current = part; current != null && current != root; current = parents.get(current)) {
            origin[0] += current.originX;
            origin[1] += current.originY;
            origin[2] += current.originZ;
        }
        return origin;
    }

    private float[] absoluteOrigin(PreparedModel prepared, BoneKind kind) {
        float[] origin = modelPivots ? prepared.pivot(kind) : null;
        return origin == null ? basePivots.get(kind).clone() : origin;
    }

    private float[] localOrigin(PreparedModel prepared, BoneKind kind) {
        float[] origin = absoluteOrigin(prepared, kind);
        ModelPart parent = parents.get(parts.get(kind));
        for (BoneKind parentKind : BoneKind.VALUES) {
            if (parts.get(parentKind) == parent) {
                float[] parentOrigin = absoluteOrigin(prepared, parentKind);
                origin[0] -= parentOrigin[0];
                origin[1] -= parentOrigin[1];
                origin[2] -= parentOrigin[2];
                break;
            }
        }
        return origin;
    }

    private void buildParentMap(ModelPart part) {
        if (part.children == null) {
            return;
        }
        for (ModelPart child : part.children.values()) {
            parents.put(child, part);
            buildParentMap(child);
        }
    }

    /** Rest-space center-of-mass lever. Unique corners avoid weighting visible faces twice. */
    public Vector3f physicsRestLever(PhysicsBoneRuntime physics) {
        Vector3f sum = new Vector3f();
        float total = 0;
        for (ModelPart.Cuboid cube : physics.part().cuboids) {
            java.util.Set<Vector3f> corners = new java.util.HashSet<>();
            for (ModelPart.Quad face : cube.sides)
                for (ModelPart.Vertex vertex : face.vertices()) corners.add(new Vector3f(vertex.x(), vertex.y(), vertex.z()));
            if (corners.isEmpty()) continue;
            Vector3f center = new Vector3f();
            Vector3f min = new Vector3f(Float.POSITIVE_INFINITY), max = new Vector3f(Float.NEGATIVE_INFINITY);
            for (Vector3f corner : corners) { center.add(corner); min.min(corner); max.max(corner); }
            center.div(corners.size());
            Vector3f size = max.sub(min);
            float weight = Math.max(.001F, size.x * size.y * size.z);
            sum.fma(weight, center); total += weight;
        }
        return total > 0 ? sum.div(total) : sum;
    }

    /** Cumulative rotation of a part in model space, including all core/physics ancestors. */
    public Quaternionf worldRotation(ModelPart part) {
        List<ModelPart> chain = new ArrayList<>();
        ModelPart current = part;
        while (current != null && current != rootPart) {
            chain.add(current);
            current = parents.get(current);
        }
        Quaternionf result = new Quaternionf();
        for (int i = chain.size() - 1; i >= 0; i--) {
            ModelPart node = chain.get(i);
            result.mul(new Quaternionf().rotationZYX(node.roll, node.yaw, node.pitch));
        }
        return result;
    }

    private void attachPhysics(PreparedModel prepared, ModelPart[] coreParts) {
        List<PreparedModel.PhysicsPart> preparedPhysics = prepared.physicsBones();
        if (preparedPhysics.isEmpty()) {
            return;
        }
        Map<String, ModelPart> byName = new HashMap<>();
        for (PreparedModel.PhysicsPart physics : preparedPhysics) {
            ModelPart part = new ModelPart(physics.cuboids(), new HashMap<>());
            part.visible = true;
            part.hidden = false;
            byName.put(physics.name().toLowerCase(java.util.Locale.ROOT), part);
        }
        Map<String, PreparedModel.PhysicsPart> preparedByName = new HashMap<>();
        for (PreparedModel.PhysicsPart physics : preparedPhysics) {
            preparedByName.put(physics.name().toLowerCase(java.util.Locale.ROOT), physics);
        }
        PhysicsConfig config = prepared.physicsConfig();
        Map<String, PhysicsConfig.Settings> settingsByName = new HashMap<>();
        for (PreparedModel.PhysicsPart physics : preparedPhysics) {
            String key = physics.name().toLowerCase(java.util.Locale.ROOT);
            ModelPart part = byName.get(key);
            if (part == null) {
                continue;
            }
            PhysicsConfig.Settings settings = config.resolved(physics.name());
            if (physics.parentPhysics() != null && !config.hasBoneOverride(physics.name())) {
                PhysicsConfig.Settings parentSettings =
                        settingsByName.get(physics.parentPhysics().toLowerCase(java.util.Locale.ROOT));
                if (parentSettings != null && "cloth".equalsIgnoreCase(parentSettings.mode)) {
                    settings = parentSettings.copy();
                }
            }
            settingsByName.put(key, settings);
            ModelPart parent;
            float parentX;
            float parentY;
            float parentZ;
            if (physics.parentPhysics() != null) {
                String parentKey = physics.parentPhysics().toLowerCase(java.util.Locale.ROOT);
                parent = byName.get(parentKey);
                PreparedModel.PhysicsPart parentPhysics = preparedByName.get(parentKey);
                if (parent == null || parentPhysics == null) {
                    continue;
                }
                parentX = parentPhysics.pivotX();
                parentY = parentPhysics.pivotY();
                parentZ = parentPhysics.pivotZ();
            } else {
                parent = coreParts[physics.parentBone().ordinal()];
                float[] pivot = absoluteOrigin(prepared, physics.parentBone());
                parentX = pivot[0];
                parentY = pivot[1];
                parentZ = pivot[2];
            }
            part.setOrigin(physics.pivotX() - parentX, physics.pivotY() - parentY,
                    physics.pivotZ() - parentZ);
            Map<String, ModelPart> children = parent.children;
            if (children == null) {
                children = new HashMap<>();
                parent.children = children;
            }
            try {
                children.put("pm_physics$" + key, part);
            } catch (UnsupportedOperationException e) {
                Map<String, ModelPart> copy = new HashMap<>(children);
                copy.put("pm_physics$" + key, part);
                parent.children = copy;
            }
            physicsBones.add(new PhysicsBoneRuntime(physics.name(), part, parent,
                    physics.parentBone(), physics.parentPhysics(),
                    physics.pivotX(), physics.pivotY(), physics.pivotZ(), settings,
                    physics.staticRotation()));
        }
    }

    public List<PhysicsBoneRuntime> physicsBones() {
        return List.copyOf(physicsBones);
    }

    public JpmFaceModel faceModel() {
        return face;
    }

    /**
     * Face-only tree for the separate-texture render pass. The wrapper chain reproduces every
     * transform from the model root down to the head, so the face inherits exactly the same
     * ancestor/root rotation and translation as the inline face.
     */
    public ModelPart faceSnapshot() {
        if (face == null) return null;
        ModelPart current = dev.playermodel.client.AppearanceManager.copyPart(face.root());
        ModelPart head = parts.get(BoneKind.HEAD);
        ModelPart node = head;
        while (node != null) {
            current = wrapTransform(node, current);
            if (node == rootPart) break;
            node = parents.get(node);
        }
        return current;
    }

    static ModelPart wrapTransform(ModelPart source, ModelPart child) {
        Map<String, ModelPart> children = new LinkedHashMap<>();
        children.put("pm_face_child", child);
        ModelPart wrapper = new ModelPart(List.of(), children);
        wrapper.setTransform(source.getTransform());
        wrapper.xScale = source.xScale;
        wrapper.yScale = source.yScale;
        wrapper.zScale = source.zScale;
        wrapper.visible = source.visible;
        wrapper.hidden = source.hidden;
        return wrapper;
    }

    public void animateFace(JpmEvalContext context) {
        if (face != null) face.animate(context);
        for (JpmFaceModel base : jemBaseFaces) {
            base.animate(context);
            // The JEM base head is a child of the custom head; carry the JPM head rotation once
            // at the custom-head level and keep this child tree unrotated.
            for (ModelPart root : base.root().children.values()) {
                root.pitch = 0;
                root.yaw = 0;
                root.roll = 0;
            }
        }
        for (JemBody body : jemBodies) {
            body.model().animate(context);
        }
        applyJpmHeadAnimation(context);
    }

    /** JustExpressions drives an extra head yaw/pitch in the JEM/JPM head animation block. */
    private void applyJpmHeadAnimation(JpmEvalContext context) {
        JpmEvalContext.PartState state = context.parts.get("head");
        if (state == null) return;
        ModelPart head = parts.get(BoneKind.HEAD);
        if (head == null) return;
        head.pitch += (float) state.rx;
        head.yaw += (float) state.ry;
        head.roll += (float) state.rz;
    }

    public void copyPose(PlayerEntityModel live, PreparedModel prepared) {
        rootPart.setTransform(live.getRootPart().getTransform());
        ModelPart[] source = ModelParts.of(live);
        float scale = prepared.rigScale();
        for (BoneKind kind : BoneKind.VALUES) {
            ModelPart from = source[kind.ordinal()];
            ModelPart to = parts.get(kind);
            if (to == null) {
                continue;
            }
            to.pitch = from.pitch;
            to.yaw = from.yaw;
            to.roll = from.roll;
            Quaternionf staticRotation = staticRotations.get(kind);
            if (staticRotation != null) {
                Quaternionf animated = new Quaternionf().rotationZYX(to.roll, to.yaw, to.pitch);
                Vector3f euler = new Quaternionf(staticRotation).mul(animated)
                        .getEulerAnglesZYX(new Vector3f());
                to.pitch = euler.x;
                to.yaw = euler.y;
                to.roll = euler.z;
            }
            // EMF/CEM player models can bake different part scales into their rest pose. Copy the
            // animation delta relative to the rest scale so the imported geometry keeps its size.
            float[] baseScale = BaseSkeleton.baseScale(from);
            to.xScale = baseScale[0] == 0 ? 1.0F : from.xScale / baseScale[0];
            to.yScale = baseScale[1] == 0 ? 1.0F : from.yScale / baseScale[1];
            to.zScale = baseScale[2] == 0 ? 1.0F : from.zScale / baseScale[2];
            float[] base = BaseSkeleton.basePivot(from);
            float[] target = localOrigin(prepared, kind);
            if (target != null) {
                to.originX = target[0] + (from.originX - base[0]) * scale;
                to.originY = target[1] + (from.originY - base[1]) * scale;
                to.originZ = target[2] + (from.originZ - base[2]) * scale;
            }
            to.visible = true;
            to.hidden = false;
        }
    }

    public PlayerEntityModel model() {
        return model;
    }

}
