package dev.playermodel.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.playermodel.client.PlayerModelClient;
import dev.playermodel.util.CameraPickContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Scope optical picking without replacing vanilla attack-range, occlusion or reach rules.
 * 1.21.1 performs picking from GameRenderer.updateCrosshairTarget.
 */
@Mixin(GameRenderer.class)
public abstract class CameraCrosshairMixin {
    @WrapMethod(method = "updateCrosshairTarget(F)V")
    private void playermodel$opticalPick(float delta, Operation<Void> original) {
        MinecraftClient client = MinecraftClient.getInstance();
        var manager = PlayerModelClient.appearanceManager();
        Entity player = client.getCameraEntity();
        var model = player == null || player != client.player ? null : manager.preparedFor(player.getUuid());
        if (model == null || !model.usesModelPivots() || !manager.viewEnabled()
                || !client.gameRenderer.getCamera().isReady()) {
            original.call(delta);
            return;
        }
        CameraPickContext.Pick previous = CameraPickContext.CURRENT.get();
        var camera = client.gameRenderer.getCamera();
        CameraPickContext.CURRENT.set(CameraPickContext.create(player, camera.getPos(), camera.getRotation(),
                player.getCameraPosVec(delta), client.options.getPerspective().isFirstPerson()));
        try {
            original.call(delta);
        } finally {
            if (previous == null) CameraPickContext.CURRENT.remove();
            else CameraPickContext.CURRENT.set(previous);
        }
    }
}
