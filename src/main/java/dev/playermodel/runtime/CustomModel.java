package dev.playermodel.runtime;

import dev.playermodel.model.BoneKind;
import dev.playermodel.model.PhysicsConfig;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A standalone player model containing only the imported cube geometry. It is driven by copying the
 * animated transforms from the live (vanilla or EMF/CEM) player model every frame, so resource-pack
 * animations keep working while the visible geometry is the imported one.
 */
public final class CustomModel {
    private final PlayerEntityModel<AbstractClientPlayerEntity> model;
    private final Map<BoneKind, ModelPart> parts = new EnumMap<>(BoneKind.class);
    private final Map<BoneKind, float[]> basePivots = new EnumMap<>(BoneKind.class);
    private final boolean modelPivots;
    private final float rigScale;
    private final List<PhysicsBoneRuntime> physicsBones = new ArrayList<>();
    private final Map<BoneKind, Quaternionf> staticRotations = new EnumMap<>(BoneKind.class);
    private final Map<ModelPart, ModelPart> parents = new IdentityHashMap<>();
    private final ModelPart rootPart;

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
        this.model = new PlayerEntityModel<>(root, prepared.slim());
        this.rigScale = prepared.rigScale();
        ModelPart[] array = ModelParts.of(model);
        List<ModelPart.Cuboid> defaultCloak = array[BoneKind.CLOAK.ordinal()].cuboids;
        List<ModelPart.Cuboid> defaultEar = array[BoneKind.EAR.ordinal()].cuboids;
        for (BoneKind kind : BoneKind.VALUES) {
            parts.put(kind, array[kind.ordinal()]);
            basePivots.put(kind, new float[]{array[kind.ordinal()].pivotX, array[kind.ordinal()].pivotY, array[kind.ordinal()].pivotZ});
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
            if (part.cuboids.isEmpty() && kind == BoneKind.CLOAK) {
                part.cuboids = defaultCloak;
            }
            if (part.cuboids.isEmpty() && kind == BoneKind.EAR) {
                part.cuboids = defaultEar;
            }
            if (modelPivots) {
                float[] pivot = prepared.pivot(kind);
                if (pivot != null) {
                    part.setPivot(pivot[0], pivot[1], pivot[2]);
                }
            }
            part.visible = true;
            part.hidden = false;
        }
        attachPhysics(prepared, array);
        buildParentMap(rootPart);
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
                float[] pivot = modelPivots ? prepared.pivot(physics.parentBone())
                        : basePivots.get(physics.parentBone());
                if (pivot == null) {
                    pivot = new float[]{0, 0, 0};
                }
                parentX = pivot[0];
                parentY = pivot[1];
                parentZ = pivot[2];
            }
            part.setPivot(physics.pivotX() - parentX, physics.pivotY() - parentY,
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

    /** Checks physics vertices that enter a core part they were outside of at rest. */
    public boolean collides(PhysicsBoneRuntime physics) {
        List<Aabb> boxes = new ArrayList<>();
        for (BoneKind kind : new BoneKind[]{BoneKind.HEAD, BoneKind.BODY, BoneKind.RIGHT_ARM,
                BoneKind.LEFT_ARM, BoneKind.RIGHT_LEG, BoneKind.LEFT_LEG}) {
            ModelPart core = parts.get(kind);
            Aabb box = core == null ? null : partAabb(core);
            if (box != null) {
                boxes.add(box);
            }
        }
        if (boxes.isEmpty()) {
            return false;
        }
        ModelPart part = physics.part();
        List<Vector3f> current = partPoints(part);
        if (current.isEmpty()) {
            return false;
        }
        float oldPitch = part.pitch;
        float oldYaw = part.yaw;
        float oldRoll = part.roll;
        part.pitch = 0.0F;
        part.yaw = 0.0F;
        part.roll = 0.0F;
        List<Vector3f> rest = partPoints(part);
        part.pitch = oldPitch;
        part.yaw = oldYaw;
        part.roll = oldRoll;
        int count = Math.min(current.size(), rest.size());
        for (int i = 0; i < count; i++) {
            for (Aabb box : boxes) {
                float currentDepth = box.depth(current.get(i));
                float restDepth = box.depth(rest.get(i));
                if (currentDepth > 0.0F && currentDepth > Math.max(0.0F, restDepth) + 0.02F) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the largest fraction of a target rotation that does not push a physics vertex into
     * a core part that it was outside of at rest.
     */
    public float collisionScale(PhysicsBoneRuntime physics, float pitch, float yaw, float roll) {
        ModelPart part = physics.part();
        float oldPitch = part.pitch;
        float oldYaw = part.yaw;
        float oldRoll = part.roll;
        float scale = 1.0F;
        for (int i = 0; i < 8; i++) {
            part.pitch = (float) Math.toRadians(pitch * scale);
            part.yaw = (float) Math.toRadians(yaw * scale);
            part.roll = (float) Math.toRadians(roll * scale);
            if (!collides(physics)) {
                break;
            }
            scale *= 0.5F;
        }
        part.pitch = oldPitch;
        part.yaw = oldYaw;
        part.roll = oldRoll;
        return scale;
    }

    private List<Vector3f> partPoints(ModelPart part) {
        if (part.cuboids == null || part.cuboids.isEmpty()) {
            return List.of();
        }
        Matrix4f matrix = partMatrix(part);
        List<Vector3f> result = new ArrayList<>();
        for (ModelPart.Cuboid cuboid : part.cuboids) {
            for (ModelPart.Quad quad : cuboid.sides) {
                for (ModelPart.Vertex vertex : quad.vertices) {
                    result.add(matrix.transformPosition(new Vector3f(vertex.pos)));
                }
            }
        }
        return result;
    }

    private boolean isAncestor(ModelPart candidate, ModelPart part) {
        ModelPart current = parents.get(part);
        while (current != null) {
            if (current == candidate) {
                return true;
            }
            current = parents.get(current);
        }
        return false;
    }

    private Aabb partAabb(ModelPart part) {
        if (part.cuboids == null || part.cuboids.isEmpty()) {
            return null;
        }
        Matrix4f matrix = partMatrix(part);
        Aabb result = new Aabb();
        for (ModelPart.Cuboid cuboid : part.cuboids) {
            for (ModelPart.Quad quad : cuboid.sides) {
                for (ModelPart.Vertex vertex : quad.vertices) {
                    Vector3f point = matrix.transformPosition(new Vector3f(vertex.pos));
                    result.include(point.x, point.y, point.z);
                }
            }
        }
        return result.valid ? result : null;
    }

    private Matrix4f partMatrix(ModelPart part) {
        List<ModelPart> chain = new ArrayList<>();
        ModelPart current = part;
        while (current != null && current != rootPart) {
            chain.add(current);
            current = parents.get(current);
        }
        Matrix4f matrix = new Matrix4f();
        for (int i = chain.size() - 1; i >= 0; i--) {
            ModelPart node = chain.get(i);
            matrix.translate(node.pivotX, node.pivotY, node.pivotZ);
            matrix.rotate(new Quaternionf().rotationZYX(node.roll, node.yaw, node.pitch));
            matrix.scale(node.xScale, node.yScale, node.zScale);
        }
        return matrix;
    }

    private static final class Aabb {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        boolean valid;

        void include(float x, float y, float z) {
            valid = true;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        float depth(Vector3f point) {
            if (point.x < minX || point.x > maxX || point.y < minY || point.y > maxY
                    || point.z < minZ || point.z > maxZ) {
                return -1.0F;
            }
            float dx = Math.min(point.x - minX, maxX - point.x);
            float dy = Math.min(point.y - minY, maxY - point.y);
            float dz = Math.min(point.z - minZ, maxZ - point.z);
            return Math.min(dx, Math.min(dy, dz));
        }

        boolean intersects(Aabb other, float margin) {
            return maxX + margin > other.minX && minX - margin < other.maxX
                    && maxY + margin > other.minY && minY - margin < other.maxY
                    && maxZ + margin > other.minZ && minZ - margin < other.maxZ;
        }
    }

    public void copyPose(PlayerEntityModel<?> live, PreparedModel prepared) {
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
            float[] target = modelPivots ? prepared.pivot(kind) : basePivots.get(kind);
            if (target != null) {
                to.pivotX = target[0] + (from.pivotX - base[0]) * scale;
                to.pivotY = target[1] + (from.pivotY - base[1]) * scale;
                to.pivotZ = target[2] + (from.pivotZ - base[2]) * scale;
            }
            to.visible = true;
            to.hidden = false;
        }
        this.model.handSwingProgress = live.handSwingProgress;
        this.model.riding = live.riding;
        this.model.child = live.child;
    }

    public PlayerEntityModel<AbstractClientPlayerEntity> model() {
        return model;
    }

    /**
     * Temporarily installs the custom cubes/pivots on the live model for the base body render and
     * all feature layers (armor, held items, cape). Restore with {@link Installed#restore()}.
     */
    public Installed install(PlayerEntityModel<?> live) {
        ModelPart[] target = ModelParts.of(live);
        ModelPart[] source = ModelParts.of(model);
        List<PartState> states = new java.util.ArrayList<>();
        for (BoneKind kind : BoneKind.VALUES) {
            if (kind == BoneKind.EAR) {
                continue;
            }
            ModelPart to = target[kind.ordinal()];
            ModelPart from = source[kind.ordinal()];
            states.add(new PartState(to, to.cuboids, to.pivotX, to.pivotY, to.pivotZ,
                    to.xScale, to.yScale, to.zScale));
            if (kind == BoneKind.CLOAK) {
                // Keep the vanilla/EMF cloak geometry but scale it around the custom body back.
                ModelPart body = source[BoneKind.BODY.ordinal()];
                to.pivotX = body.pivotX;
                to.pivotY = body.pivotY;
                to.pivotZ = body.pivotZ - 2.5F * rigScale;
                to.xScale = rigScale;
                to.yScale = rigScale;
                to.zScale = rigScale;
                continue;
            }
            to.cuboids = from.cuboids;
            to.pivotX = from.pivotX;
            to.pivotY = from.pivotY;
            to.pivotZ = from.pivotZ;
            to.xScale = from.xScale;
            to.yScale = from.yScale;
            to.zScale = from.zScale;
        }
        return new Installed(states);
    }

    public record Installed(List<PartState> states) {
        public void restore() {
            for (PartState state : states) {
                state.restore();
            }
        }
    }

    private record PartState(ModelPart part, List<ModelPart.Cuboid> cuboids, float x, float y, float z,
                             float sx, float sy, float sz) {
        void restore() {
            part.cuboids = cuboids;
            part.pivotX = x;
            part.pivotY = y;
            part.pivotZ = z;
            part.xScale = sx;
            part.yScale = sy;
            part.zScale = sz;
        }
    }
}
