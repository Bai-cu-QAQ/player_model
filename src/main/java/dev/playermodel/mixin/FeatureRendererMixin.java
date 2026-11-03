package dev.playermodel.mixin;

import dev.playermodel.client.PlayerModelClient;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Feature transforms use the pose captured for this submission, not a cached custom model. */
@Mixin(FeatureRenderer.class)
public abstract class FeatureRendererMixin {
    @Inject(method = "getContextModel", at = @At("HEAD"), cancellable = true)
    private void playermodel$context(CallbackInfoReturnable<EntityModel<?>> cir) {
        var pose = PlayerModelClient.appearanceManager().activePose();
        if (pose != null) cir.setReturnValue(pose.model());
    }
}
