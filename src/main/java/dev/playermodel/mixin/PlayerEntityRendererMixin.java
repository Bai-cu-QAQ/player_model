package dev.playermodel.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.playermodel.client.PlayerModelClient;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @ModifyReturnValue(method = "getTexture(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)Lnet/minecraft/util/Identifier;", at = @At("RETURN"))
    private Identifier playermodel$texture(Identifier original, PlayerEntityRenderState state) {
        var player = PlayerModelClient.playerFor(state);
        Identifier texture = player == null ? null : PlayerModelClient.appearanceManager().textureFor(player);
        return texture == null ? original : texture;
    }
}
