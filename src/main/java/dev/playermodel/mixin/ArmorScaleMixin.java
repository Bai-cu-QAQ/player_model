package dev.playermodel.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.playermodel.client.AppearanceManager;
import dev.playermodel.client.PlayerModelClient;
import dev.playermodel.model.Anchor;
import dev.playermodel.model.BoneKind;
import dev.playermodel.runtime.CustomModel;
import dev.playermodel.runtime.ModelParts;
import dev.playermodel.runtime.PreparedModel;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Copies custom pivots, then applies the armor anchor offset/scale to the armor model only. */
@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorScaleMixin {
    @Redirect(method = "renderArmor", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/model/BipedEntityModel;copyBipedStateTo(Lnet/minecraft/client/render/entity/model/BipedEntityModel;)V"))
    private void playermodel$copyAndScale(BipedEntityModel<?> base, BipedEntityModel<?> copy,
                                          @Local(argsOnly = true) LivingEntity entity,
                                          @Local(argsOnly = true) EquipmentSlot slot) {
        //noinspection unchecked,rawtypes
        ((BipedEntityModel) base).copyBipedStateTo((BipedEntityModel) copy);
        if (!(entity instanceof AbstractClientPlayerEntity player)) {
            return;
        }
        AppearanceManager manager = PlayerModelClient.appearanceManager();
        CustomModel custom = manager.customModelFor(player);
        PreparedModel prepared = manager.preparedFor(player.getUuid());
        if (custom == null || prepared == null) {
            return;
        }
        ModelPart[] parts = ModelParts.of(custom.model());
        String anchorName = switch (slot) {
            case HEAD -> Anchor.HELMET;
            case CHEST -> Anchor.CHEST;
            case LEGS -> Anchor.LEGS;
            case FEET -> Anchor.BOOTS;
            default -> null;
        };
        Anchor anchor = anchorName == null ? null : prepared.anchor(anchorName);
        float scale = anchor != null && anchor.scale() > 0 ? anchor.scale() : prepared.rigScale();
        float dx = 0;
        float dy = 0;
        float dz = 0;
        if (prepared.customAnchors() && anchor != null && (slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST)) {
            ModelPart bone = parts[anchor.bone().ordinal()];
            dx = anchor.x() - bone.pivotX;
            dy = anchor.y() - bone.pivotY;
            dz = anchor.z() - bone.pivotZ;
        } else if (prepared.customAnchors() && anchor != null) {
            // Legs/feet share one anchor; only apply Y/Z so one boot is not pushed sideways.
            ModelPart bone = parts[slot == EquipmentSlot.FEET
                    ? BoneKind.RIGHT_LEG.ordinal() : BoneKind.BODY.ordinal()];
            dy = anchor.y() - bone.pivotY;
            dz = anchor.z() - bone.pivotZ;
        }
        switch (slot) {
            case HEAD -> {
                apply(copy.head, parts[BoneKind.HEAD.ordinal()], dx, dy, dz, scale);
                apply(copy.hat, parts[BoneKind.HEAD.ordinal()], dx, dy, dz, scale);
            }
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
            default -> {
            }
        }
    }

    private static void apply(ModelPart target, ModelPart source, float dx, float dy, float dz, float scale) {
        target.pivotX = source.pivotX + dx;
        target.pivotY = source.pivotY + dy;
        target.pivotZ = source.pivotZ + dz;
        target.xScale = scale;
        target.yScale = scale;
        target.zScale = scale;
    }
}
