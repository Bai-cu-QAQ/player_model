package dev.playermodel.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.playermodel.client.AppearanceManager;
import dev.playermodel.client.PlayerModelClient;
import dev.playermodel.model.Anchor;
import dev.playermodel.model.BoneKind;
import dev.playermodel.runtime.ModelParts;
import dev.playermodel.runtime.PreparedModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.equipment.EquipmentModel;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.registry.RegistryKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** EquipmentRenderer still owns layers, dyes, trims and glint; only its submitted pose changes. */
@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorScaleMixin {
    @Redirect(method = "renderArmor", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/equipment/EquipmentRenderer;render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;II)V"))
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void playermodel$armor(EquipmentRenderer renderer, EquipmentModel.LayerType type,
                                   RegistryKey<EquipmentAsset> asset, Model model, Object state,
                                   ItemStack stack, MatrixStack matrices, OrderedRenderCommandQueue queue,
                                   int light, int outline, @Local(argsOnly = true) EquipmentSlot slot) {
        var pose = PlayerModelClient.appearanceManager().activePose();
        Model submitted = model;
        if (pose != null && model instanceof BipedEntityModel<?> && state instanceof BipedEntityRenderState bipedState) {
            model.setAngles(bipedState);
            BipedEntityModel<BipedEntityRenderState> copy = new BipedEntityModel<>(AppearanceManager.copyPart(model.getRootPart()));
            applyAnchor(copy, ModelParts.of(pose.model()), pose.prepared(), slot);
            submitted = AppearanceManager.frozenModel(copy.getRootPart());
        }
        renderer.render(type, asset, submitted, state, stack, matrices, queue, light, outline);
    }

    private static void applyAnchor(BipedEntityModel<?> copy, ModelPart[] parts, PreparedModel prepared, EquipmentSlot slot) {
        String name = switch (slot) {
            case HEAD -> Anchor.HELMET;
            case CHEST -> Anchor.CHEST;
            case LEGS -> Anchor.LEGS;
            case FEET -> Anchor.BOOTS;
            default -> null;
        };
        Anchor anchor = name == null ? null : prepared.anchor(name);
        float scale = anchor != null && anchor.scale() > 0 ? anchor.scale() : prepared.rigScale();
        float dx = 0, dy = 0, dz = 0;
        if (prepared.customAnchors() && anchor != null) {
            boolean single = slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST;
            ModelPart bone = parts[(single ? anchor.bone() : slot == EquipmentSlot.FEET ? BoneKind.RIGHT_LEG : BoneKind.BODY).ordinal()];
            if (single) dx = anchor.x() - bone.originX;
            dy = anchor.y() - bone.originY;
            dz = anchor.z() - bone.originZ;
        }
        switch (slot) {
            case HEAD -> apply(copy.head, parts[BoneKind.HEAD.ordinal()], dx, dy, dz, scale);
            case CHEST -> {
                apply(copy.body, parts[BoneKind.BODY.ordinal()], dx, dy, dz, scale);
                apply(copy.rightArm, parts[BoneKind.RIGHT_ARM.ordinal()], dx, dy, dz, scale);
                apply(copy.leftArm, parts[BoneKind.LEFT_ARM.ordinal()], dx, dy, dz, scale);
            }
            case LEGS -> {
                apply(copy.body, parts[BoneKind.BODY.ordinal()], dx, dy, dz, scale);
                apply(copy.rightLeg, parts[BoneKind.RIGHT_LEG.ordinal()], dx, dy, dz, scale);
                apply(copy.leftLeg, parts[BoneKind.LEFT_LEG.ordinal()], dx, dy, dz, scale);
            }
            case FEET -> {
                apply(copy.rightLeg, parts[BoneKind.RIGHT_LEG.ordinal()], dx, dy, dz, scale);
                apply(copy.leftLeg, parts[BoneKind.LEFT_LEG.ordinal()], dx, dy, dz, scale);
            }
            default -> { }
        }
    }

    private static void apply(ModelPart target, ModelPart source, float dx, float dy, float dz, float scale) {
        target.setTransform(source.getTransform());
        target.originX += dx;
        target.originY += dy;
        target.originZ += dz;
        target.xScale = source.xScale * scale;
        target.yScale = source.yScale * scale;
        target.zScale = source.zScale * scale;
    }
}
