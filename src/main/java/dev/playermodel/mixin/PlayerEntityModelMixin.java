package dev.playermodel.mixin;

import dev.playermodel.client.PlayerModelClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the live model's rest pose; the live model itself is never modified. */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {
    @Inject(method = "setAngles", at = @At("HEAD"))
    private void playermodel$captureBase(LivingEntity entity, float limbAngle, float limbDistance,
                                         float animationProgress, float headYaw, float headPitch,
                                         CallbackInfo ci) {
        PlayerModelClient.appearanceManager().captureBase(entity, (PlayerEntityModel<?>) (Object) this);
    }
}
