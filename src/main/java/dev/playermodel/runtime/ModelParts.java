package dev.playermodel.runtime;

import dev.playermodel.model.BoneKind;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import dev.playermodel.mixin.PlayerEntityModelAccessor;

/** Resolves the fourteen vanilla model parts, including the private cloak/ear parts. */
public final class ModelParts {
    private ModelParts() {
    }

    public static ModelPart[] of(PlayerEntityModel<?> model) {
        PlayerEntityModelAccessor accessor = (PlayerEntityModelAccessor) (Object) model;
        return new ModelPart[]{
                model.head,
                model.hat,
                model.body,
                model.rightArm,
                model.leftArm,
                model.rightLeg,
                model.leftLeg,
                model.rightSleeve,
                model.leftSleeve,
                model.rightPants,
                model.leftPants,
                model.jacket,
                accessor.playermodel$getCloak(),
                accessor.playermodel$getEar()
        };
    }

    public static ModelPart part(PlayerEntityModel<?> model, BoneKind kind) {
        return of(model)[kind.ordinal()];
    }
}
