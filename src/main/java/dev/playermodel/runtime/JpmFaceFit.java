package dev.playermodel.runtime;

import dev.playermodel.jpm.JpmModel;
import dev.playermodel.jpm.JpmPart;
import dev.playermodel.model.BoneKind;
import dev.playermodel.model.NormalizedBone;
import dev.playermodel.model.NormalizedCube;

/**
 * Aligns the JPM face overlay to the imported head using a hidden base-face reference.
 * The reference is derived from the head cube bounds, so no extra face box is rendered.
 */
public final class JpmFaceFit {
    private static final float VANILLA_HEAD_HEIGHT = 8.0F;
    /** Vanilla skin eye rows sit about 56% down the 8px face. */
    private static final float FACE_EYE_FRACTION = 0.5625F;

    private JpmFaceFit() {
    }

    public static float scale(PreparedModel prepared, JpmModel face) {
        float[] bounds = headYBounds(prepared);
        if (bounds == null) return 1.0F;
        float height = bounds[1] - bounds[0];
        if (!(height > 0)) return 1.0F;
        return Math.max(0.2F, Math.min(2.0F, height / VANILLA_HEAD_HEIGHT));
    }

    public static float offsetY(PreparedModel prepared, JpmModel face) {
        float[] bounds = headYBounds(prepared);
        if (bounds == null || face == null) return 0;
        float targetEyeY = bounds[0] + (bounds[1] - bounds[0]) * FACE_EYE_FRACTION;
        float[] eyeBand = jpmEyeYBounds(face);
        if (eyeBand == null) return 0;
        float jpmEyeCenter = (eyeBand[0] + eyeBand[1]) * 0.5F;
        return targetEyeY - jpmEyeCenter;
    }

    private static float[] headYBounds(PreparedModel prepared) {
        if (prepared == null || prepared.data() == null) return null;
        NormalizedBone head = prepared.data().bone(BoneKind.HEAD);
        if (head == null || head.cubes().isEmpty()) return null;
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (NormalizedCube cube : head.cubes()) {
            min = Math.min(min, cube.minY());
            max = Math.max(max, cube.maxY());
        }
        return Float.isFinite(min) && Float.isFinite(max) && max > min ? new float[]{min, max} : null;
    }

    private static float[] jpmEyeYBounds(JpmModel model) {
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (JpmPart root : model.roots) {
            float[] result = collect(root, 0);
            if (result != null) {
                min = Math.min(min, result[0]);
                max = Math.max(max, result[1]);
            }
        }
        return Float.isFinite(min) && Float.isFinite(max) && max > min ? new float[]{min, max} : null;
    }

    private static float[] collect(JpmPart part, float parentY) {
        float partY = parentY + part.translate[1];
        boolean eyePart = part.id.matches("[rl]_(eye_white|pupil|eyelid|blink)\\d+");
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        if (eyePart) {
            for (var box : part.boxes) {
                min = Math.min(min, partY + box.y());
                max = Math.max(max, partY + box.y() + box.height());
            }
        }
        for (JpmPart child : part.children) {
            float[] childBounds = collect(child, partY);
            if (childBounds != null) {
                min = Math.min(min, childBounds[0]);
                max = Math.max(max, childBounds[1]);
            }
        }
        return Float.isFinite(min) && Float.isFinite(max) && max > min ? new float[]{min, max} : null;
    }
}
