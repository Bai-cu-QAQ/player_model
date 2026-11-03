package dev.playermodel.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.playermodel.client.AppearanceManager;
import dev.playermodel.client.PlayerModelClient;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** All deferred commands receive a private transform tree, never the shared animated model. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Shadow protected EntityModel<?> model;

    @WrapMethod(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V")
    private void playermodel$capture(LivingEntityRenderState state, MatrixStack matrices,
                                     OrderedRenderCommandQueue queue, CameraRenderState camera,
                                     Operation<Void> original) {
        AppearanceManager manager = PlayerModelClient.appearanceManager();
        AppearanceManager.RenderPose pose = state instanceof PlayerEntityRenderState playerState
                && model instanceof PlayerEntityModel playerModel ? manager.capturePose(playerModel, playerState) : null;
        if (state instanceof AppearanceManager.PoseState holder) holder.playermodel$setPose(pose);
        manager.pushPose(pose);
        try {
            original.call(state, matrices, queue, camera);
        } finally {
            manager.popPose();
        }
    }

    @Redirect(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void playermodel$submit(OrderedRenderCommandQueue queue, Model model, Object state,
                                    MatrixStack matrices, RenderLayer layer, int light, int overlay,
                                    int color, Sprite sprite, int outline,
                                    ModelCommandRenderer.CrumblingOverlayCommand crumbling) {
        var pose = PlayerModelClient.appearanceManager().activePose();
        Model submitted = pose == null ? model : AppearanceManager.frozenModel(pose.model().getRootPart());
        queue.submitModel(submitted, state, matrices, layer, light, overlay, color, sprite, outline, crumbling);
    }
}
