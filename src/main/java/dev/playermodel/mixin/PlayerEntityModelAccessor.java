package dev.playermodel.mixin;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerEntityModel.class)
public interface PlayerEntityModelAccessor {
    @Accessor("cloak")
    ModelPart playermodel$getCloak();

    @Accessor("ear")
    ModelPart playermodel$getEar();
}
