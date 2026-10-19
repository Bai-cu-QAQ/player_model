package dev.playermodel.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.playermodel.client.AppearanceManager;
import dev.playermodel.client.PlayerModelClient;
import dev.playermodel.model.Anchor;
import dev.playermodel.model.BoneKind;
import dev.playermodel.runtime.ModelParts;
import dev.playermodel.runtime.PreparedModel;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Applies the hand anchor offset and scale before the vanilla fixed hand translation. */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemScaleMixin {
    @Redirect(method = "renderItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V"))
    private void playermodel$scaleHandOffset(MatrixStack matrices, float x, float y, float z,
                                              @Local(argsOnly = true) LivingEntity entity,
                                              @Local(argsOnly = true) Arm arm) {
        if (!(entity instanceof AbstractClientPlayerEntity player)) {
            matrices.translate(x, y, z);
            return;
        }
        AppearanceManager manager = PlayerModelClient.appearanceManager();
        PreparedModel prepared = manager.preparedFor(player.getUuid());
        if (prepared == null) {
            matrices.translate(x, y, z);
            return;
        }
        BoneKind bone = arm == Arm.LEFT ? BoneKind.LEFT_ARM : BoneKind.RIGHT_ARM;
        Anchor anchor = prepared.anchor(arm == Arm.LEFT ? Anchor.LEFT_HAND : Anchor.RIGHT_HAND);
        float scale = anchor != null && anchor.scale() > 0 ? anchor.scale() : prepared.rigScale();
        if (prepared.customAnchors() && anchor != null && manager.customModelFor(player) != null) {
            ModelPart part = ModelParts.of(manager.customModelFor(player).model())[bone.ordinal()];
            matrices.translate((anchor.x() - part.pivotX) / 16.0F,
                    (anchor.y() - part.pivotY) / 16.0F,
                    (anchor.z() - part.pivotZ) / 16.0F);
        }
        matrices.scale(scale, scale, scale);
        matrices.translate(x, y, z);
    }
}
