package dev.playermodel.runtime;

import dev.playermodel.model.BoneKind;
import net.minecraft.client.model.ModelPart;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Captures the vanilla/EMF state of the shared player model parts before the first customization.
 * Every render restores either this state or installs the per-player custom state.
 */
public final class BaseSkeleton {
    private static final Map<ModelPart, BasePart> BASE = new IdentityHashMap<>();

    private BaseSkeleton() {
    }

    public static void capture(ModelPart[] parts) {
        for (ModelPart part : parts) {
            BASE.computeIfAbsent(part, p -> new BasePart(p.cuboids, p.pivotX, p.pivotY, p.pivotZ,
                    p.xScale, p.yScale, p.zScale, p.visible, p.hidden));
        }
    }

    public static void restore(ModelPart part) {
        BasePart base = BASE.get(part);
        if (base == null) {
            return;
        }
        part.cuboids = base.cuboids;
        part.pivotX = base.pivotX;
        part.pivotY = base.pivotY;
        part.pivotZ = base.pivotZ;
    }

    public static void restore(ModelPart[] parts) {
        for (ModelPart part : parts) {
            restore(part);
        }
    }

    public static boolean has(ModelPart part) {
        return BASE.containsKey(part);
    }

    public static float[] basePivot(ModelPart part) {
        BasePart base = BASE.get(part);
        return base == null ? new float[]{part.pivotX, part.pivotY, part.pivotZ}
                : new float[]{base.pivotX, base.pivotY, base.pivotZ};
    }

    public static float[] baseScale(ModelPart part) {
        BasePart base = BASE.get(part);
        return base == null ? new float[]{part.xScale, part.yScale, part.zScale}
                : new float[]{base.xScale, base.yScale, base.zScale};
    }

    private record BasePart(List<ModelPart.Cuboid> cuboids, float pivotX, float pivotY, float pivotZ,
                            float xScale, float yScale, float zScale, boolean visible, boolean hidden) {
    }
}
