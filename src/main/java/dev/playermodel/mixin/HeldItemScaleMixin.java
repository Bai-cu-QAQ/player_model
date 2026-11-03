package dev.playermodel.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.playermodel.client.AppearanceManager;
import dev.playermodel.model.Anchor;
import dev.playermodel.model.BoneKind;
import dev.playermodel.runtime.ModelParts;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Matrices are captured by vanilla item submissions after the custom hand transform is applied. */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemScaleMixin {
    @Redirect(method = "renderItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V"))
    private void playermodel$scaleHandOffset(MatrixStack matrices, float x, float y, float z,
                                             @Local(argsOnly = true) ArmedEntityRenderState state,
                                             @Local(argsOnly = true) Arm arm) {
        var pose = AppearanceManager.pose(state);
        if (pose != null) {
            var prepared = pose.prepared();
            Anchor anchor = prepared.anchor(arm == Arm.LEFT ? Anchor.LEFT_HAND : Anchor.RIGHT_HAND);
            float scale = anchor != null && anchor.scale() > 0 ? anchor.scale() : prepared.rigScale();
            if (prepared.customAnchors() && anchor != null) {
                ModelPart part = ModelParts.of(pose.model())[(arm == Arm.LEFT ? BoneKind.LEFT_ARM : BoneKind.RIGHT_ARM).ordinal()];
                matrices.translate((anchor.x() - part.originX) / 16.0F,
                        (anchor.y() - part.originY) / 16.0F, (anchor.z() - part.originZ) / 16.0F);
            }
            matrices.scale(scale, scale, scale);
        }
        matrices.translate(x, y, z);
    }
}
