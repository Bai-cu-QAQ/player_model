package dev.playermodel.runtime;

import dev.playermodel.model.BoneKind;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.PlayerEntityModel;

import java.util.EnumMap;
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

    public CustomModel(PreparedModel prepared) {
        TexturedModelData data = TexturedModelData.of(PlayerEntityModel.getTexturedModelData(
                Dilation.NONE, prepared.slim()), 64, 64);
        ModelPart root = data.createModel();
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
