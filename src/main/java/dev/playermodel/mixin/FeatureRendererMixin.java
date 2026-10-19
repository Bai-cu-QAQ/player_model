package dev.playermodel.mixin;

import dev.playermodel.client.AppearanceManager;
import dev.playermodel.client.PlayerModelClient;
import dev.playermodel.runtime.CustomModel;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Armor, held-item and cape features ask their context for the player model. Point that context at
 * the custom model so every feature uses exactly the custom pivots instead of the live EMF model.
 */
@Mixin(FeatureRenderer.class)
public abstract class FeatureRendererMixin {
    @Inject(method = "getContextModel", at = @At("HEAD"), cancellable = true)
    private void playermodel$context(CallbackInfoReturnable<Object> cir) {
        AbstractClientPlayerEntity player = PlayerModelClient.renderingPlayer();
        if (player == null) {
            return;
        }
        AppearanceManager manager = PlayerModelClient.appearanceManager();
        CustomModel custom = manager.customModelFor(player);
        if (custom != null) {
            cir.setReturnValue(custom.model());
        }
    }
}
