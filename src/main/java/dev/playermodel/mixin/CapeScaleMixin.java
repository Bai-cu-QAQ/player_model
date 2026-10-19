package dev.playermodel.mixin;

import dev.playermodel.client.AppearanceManager;
import dev.playermodel.client.PlayerModelClient;
import dev.playermodel.model.Anchor;
import dev.playermodel.model.BoneKind;
import dev.playermodel.runtime.CustomModel;
import dev.playermodel.runtime.ModelParts;
import dev.playermodel.runtime.PreparedModel;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scale the cape around the anchor_cape pivot, never around the entity origin. */
@Mixin(value = CapeFeatureRenderer.class, priority = 2000)
public abstract class CapeScaleMixin {
    private boolean playermodel$pushed;

    @Inject(method = "render", at = @At("HEAD"))
    private void playermodel$scaleCape(MatrixStack matrices, VertexConsumerProvider provider, int light,
                                       AbstractClientPlayerEntity entity, float limbAngle, float limbDistance,
                                       float tickDelta, float animationProgress, float headYaw, float headPitch,
                                       CallbackInfo ci) {
        AppearanceManager manager = PlayerModelClient.appearanceManager();
        CustomModel custom = manager.customModelFor(entity);
        PreparedModel prepared = manager.preparedFor(entity.getUuid());
        if (custom == null || prepared == null) {
            return;
        }
        Anchor anchor = prepared.anchor(Anchor.CAPE);
        ModelPart body = ModelParts.of(custom.model())[BoneKind.BODY.ordinal()];
        boolean useAnchor = prepared.customAnchors() && anchor != null;
        float x = useAnchor ? anchor.x() : body.pivotX;
        float y = useAnchor ? anchor.y() : body.pivotY;
        float z = useAnchor ? anchor.z() : -2.0F;
        float scale = anchor != null && anchor.scale() > 0 ? anchor.scale() : prepared.rigScale();
        matrices.push();
        if (useAnchor) {
            matrices.translate(anchor.x() - body.pivotX, anchor.y() - body.pivotY, anchor.z() + 2.0F);
        }
        matrices.translate(x, y, z);
        matrices.scale(scale, scale, scale);
        matrices.translate(-x, -y, -z);
        this.playermodel$pushed = true;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void playermodel$unscaleCape(MatrixStack matrices, VertexConsumerProvider provider, int light,
                                         AbstractClientPlayerEntity entity, float limbAngle, float limbDistance,
                                         float tickDelta, float animationProgress, float headYaw, float headPitch,
                                         CallbackInfo ci) {
        if (this.playermodel$pushed) {
            matrices.pop();
            this.playermodel$pushed = false;
        }
    }
}
