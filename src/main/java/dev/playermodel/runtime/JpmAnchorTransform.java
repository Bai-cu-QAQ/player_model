package dev.playermodel.runtime;

import dev.playermodel.model.Anchor;
import dev.playermodel.model.BoneKind;

/** Computes the local head-space offset and scale for the optional JPM anchor. */
public final class JpmAnchorTransform {
    private JpmAnchorTransform() {
    }

    public static float[] offset(PreparedModel prepared, Anchor anchor, float[] fallbackHeadPivot) {
        if (anchor == null) return new float[]{0, 0, 0};
        float[] headPivot = prepared.pivot(BoneKind.HEAD);
        if (headPivot == null) headPivot = fallbackHeadPivot;
        if (headPivot == null) headPivot = new float[]{0, 0, 0};
        return new float[]{anchor.x() - headPivot[0], anchor.y() - headPivot[1], anchor.z() - headPivot[2]};
    }

    public static float scale(PreparedModel prepared, Anchor anchor) {
        if (anchor == null) return 1.0F;
        if (anchor.scale() > 0) return anchor.scale();
        return prepared.rigScale() > 0 ? prepared.rigScale() : 1.0F;
    }
}
