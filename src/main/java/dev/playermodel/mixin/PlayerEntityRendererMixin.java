package dev.playermodel.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.playermodel.client.PlayerModelClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @ModifyReturnValue(method = "getTexture", at = @At("RETURN"))
    private Identifier playermodel$texture(Identifier original, AbstractClientPlayerEntity player) {
        Identifier texture = PlayerModelClient.appearanceManager().textureFor(player);
        return texture == null ? original : texture;
    }
}
