package dev.playermodel.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.playermodel.client.PlayerModelClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces the rendered base body of custom-model players and supplies their texture. The live
 * model still receives setAngles and EMF/CEM animation updates; the custom model copies its pose.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "getRenderLayer", at = @At("HEAD"), cancellable = true)
    private void playermodel$renderLayer(LivingEntity entity, boolean showBody, boolean translucent,
                                         boolean showOutline, CallbackInfoReturnable<RenderLayer> cir) {
        if (entity instanceof AbstractClientPlayerEntity player) {
            RenderLayer layer = PlayerModelClient.appearanceManager().renderLayer(player);
            if (layer != null) {
                cir.setReturnValue(layer);
            }
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void playermodel$setRenderingPlayer(LivingEntity entity, float tickDelta, float yaw, MatrixStack matrices,
                                                 VertexConsumerProvider provider, int light, CallbackInfo ci) {
        if (entity instanceof net.minecraft.client.network.AbstractClientPlayerEntity player) {
            PlayerModelClient.setRenderingPlayer(player);
        } else {
            PlayerModelClient.setRenderingPlayer(null);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void playermodel$restoreLive(LivingEntity entity, float tickDelta, float yaw, MatrixStack matrices,
                                          VertexConsumerProvider provider, int light, CallbackInfo ci) {
        PlayerModelClient.setRenderingPlayer(null);
        PlayerModelClient.restoreLive();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"))
    private void playermodel$renderModel(EntityModel<?> model, MatrixStack matrices, VertexConsumer vertices,
                                         int light, int overlay, int color,
                                         @Local(argsOnly = true) LivingEntity entity) {
        boolean custom = PlayerModelClient.appearanceManager()
                .renderCustom(model, matrices, vertices, light, overlay, color, entity);
        if (!custom) {
            model.render(matrices, vertices, light, overlay, color);
        }
    }
}
