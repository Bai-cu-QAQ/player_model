package dev.playermodel.runtime;

import dev.playermodel.model.Anchor;
import dev.playermodel.model.BoneKind;
import net.minecraft.client.model.ModelPart;

/** A head-local attachment transform outside the JPM animation tree. */
public final class JpmAnchorTransform {
    private JpmAnchorTransform() { }

    public static void apply(PreparedModel prepared, ModelPart target, float[] headAbsoluteOrigin) {
        Anchor anchor = prepared.anchor(Anchor.JPM);
        if (anchor == null) {
            target.setOrigin(0, 0, 0);
            target.xScale = target.yScale = target.zScale = 1;
            return;
        }
        float[] head = headAbsoluteOrigin == null ? new float[]{0, 0, 0} : headAbsoluteOrigin;
        target.setOrigin(anchor.x() - head[0], anchor.y() - head[1], anchor.z() - head[2]);
        float scale = Float.isFinite(anchor.scale()) && anchor.scale() > 0 ? anchor.scale() : 1;
        target.xScale = target.yScale = target.zScale = scale;
    }
}