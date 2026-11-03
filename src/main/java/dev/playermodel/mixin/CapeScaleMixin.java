package dev.playermodel.mixin;

import dev.playermodel.client.AppearanceManager;
import dev.playermodel.model.Anchor;
import dev.playermodel.model.BoneKind;
import dev.playermodel.runtime.ModelParts;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Capture vanilla cape animation and the custom anchor into this queue submission. */
@Mixin(value = CapeFeatureRenderer.class, priority = 2000)
public abstract class CapeScaleMixin {
    @Redirect(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/PlayerEntityRenderState;FF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void playermodel$cape(OrderedRenderCommandQueue queue, Model model, Object state,
                                  MatrixStack matrices, RenderLayer layer, int light, int overlay,
                                  int outline, ModelCommandRenderer.CrumblingOverlayCommand crumbling) {
        var pose = state instanceof PlayerEntityRenderState playerState ? AppearanceManager.pose(playerState) : null;
        if (pose == null) {
            queue.submitModel(model, state, matrices, layer, light, overlay, outline, crumbling);
            return;
        }
        var prepared = pose.prepared();
        var body = ModelParts.of(pose.model())[BoneKind.BODY.ordinal()];
        Anchor anchor = prepared.anchor(Anchor.CAPE);
        boolean useAnchor = prepared.customAnchors() && anchor != null;
        float x = (useAnchor ? anchor.x() : body.originX) / 16.0F;
        float y = (useAnchor ? anchor.y() : body.originY) / 16.0F;
        float z = (useAnchor ? anchor.z() : -2.0F) / 16.0F;
        float scale = anchor != null && anchor.scale() > 0 ? anchor.scale() : prepared.rigScale();
        model.setAngles(state);
        Model frozen = AppearanceManager.frozenModel(model.getRootPart());
        matrices.push();
        try {
            if (useAnchor) matrices.translate((anchor.x() - body.originX) / 16.0F,
                    (anchor.y() - body.originY) / 16.0F, (anchor.z() + 2.0F) / 16.0F);
            matrices.translate(x, y, z);
            matrices.scale(scale, scale, scale);
            matrices.translate(-x, -y, -z);
            queue.submitModel(frozen, state, matrices, layer, light, overlay, outline, crumbling);
        } finally {
            matrices.pop();
        }
    }
}
