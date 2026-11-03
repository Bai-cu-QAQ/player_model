package dev.playermodel.mixin;

import dev.playermodel.client.AppearanceManager;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** The pose belongs to one player render-state submission; commands hold their own deep copy. */
@Mixin(PlayerEntityRenderState.class)
public abstract class PlayerRenderPoseMixin implements AppearanceManager.PoseState {
    @Unique private AppearanceManager.RenderPose playermodel$pose;

    @Override public AppearanceManager.RenderPose playermodel$getPose() { return playermodel$pose; }
    @Override public void playermodel$setPose(AppearanceManager.RenderPose pose) { playermodel$pose = pose; }
}
