package dev.playermodel.runtime;

import dev.playermodel.model.BoneKind;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Resolves the core parts and stable, empty compatibility bones for cape and ears. */
public final class ModelParts {
    private ModelParts() {
    }

    public static ModelPart[] of(PlayerEntityModel model) {
        // Capes and ears have separate feature models in 1.21.11. Keep imported bones in the
        // player's own hierarchy instead of borrowing (or generating) feature geometry.
        ModelPart root = model.getRootPart();
        ModelPart cloak = compatibilityPart(root, "pm_cloak");
        ModelPart ear = compatibilityPart(root, "pm_ear");
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
                cloak,
                ear
        };
    }

    private static ModelPart compatibilityPart(ModelPart root, String name) {
        ModelPart part = root.children.get(name);
        if (part == null) {
            part = new ModelPart(List.of(), new HashMap<>());
            Map<String, ModelPart> children = new HashMap<>(root.children);
            children.put(name, part);
            root.children = children;
        }
        return part;
    }

    public static ModelPart part(PlayerEntityModel model, BoneKind kind) {
        return of(model)[kind.ordinal()];
    }
}
