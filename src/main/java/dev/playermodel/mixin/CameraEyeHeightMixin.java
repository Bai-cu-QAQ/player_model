package dev.playermodel.mixin;

import dev.playermodel.client.PlayerModelClient;
import dev.playermodel.runtime.PreparedModel;
import dev.playermodel.util.ModelCameraHeight;
import net.minecraft.entity.EntityPose;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Replaces only the target eye-height sample used by the model camera in all perspectives.
 * Camera.updateEyeHeight still performs Minecraft's interpolation and pose/camera transformations.
 */
@Mixin(Camera.class)
public abstract class CameraEyeHeightMixin {
    @Redirect(method = "updateEyeHeight", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;getStandingEyeHeight()F"))
    private float playermodel$modelEyeTarget(Entity entity) {
        float vanilla = entity.getStandingEyeHeight();
        if (!(entity instanceof ClientPlayerEntity player)) {
            return vanilla;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (player != client.player) {
            return vanilla;
        }
        var manager = PlayerModelClient.appearanceManager();
        if (!manager.viewEnabled()) {
            return vanilla;
        }
        PreparedModel prepared = manager.preparedFor(player.getUuid());
        if (prepared == null || !prepared.usesModelPivots()) {
            return vanilla;
        }
        // Despite its name, getStandingEyeHeight returns the cached CURRENT-pose eye height.
        // Keep that pose/attribute-scale factor rather than replacing it with a standing constant.
        float baseStandingEye = player.getBaseDimensions(EntityPose.STANDING).eyeHeight();
        return ModelCameraHeight.adapt(vanilla, baseStandingEye, prepared.eyeHeight());
    }
}
