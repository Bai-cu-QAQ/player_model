package dev.playermodel.mixin;

import dev.playermodel.client.PlayerModelClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the rest pose before state-driven animation, including deferred vanilla draws. */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {
    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("HEAD"))
    private void playermodel$captureBase(PlayerEntityRenderState state, CallbackInfo ci) {
        var player = PlayerModelClient.playerFor(state);
        if (player != null) PlayerModelClient.appearanceManager().captureBase(player, (PlayerEntityModel) (Object) this);
    }
}
