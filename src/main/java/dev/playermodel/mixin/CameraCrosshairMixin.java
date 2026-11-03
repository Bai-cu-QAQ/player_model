package dev.playermodel.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.playermodel.client.PlayerModelClient;
import dev.playermodel.util.CameraPickContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;

/** Scope optical picking without replacing vanilla attack-range, occlusion or reach rules. */
@Mixin(ClientPlayerEntity.class)
public abstract class CameraCrosshairMixin {
    @WrapMethod(method = "getCrosshairTarget(FLnet/minecraft/entity/Entity;)Lnet/minecraft/util/hit/HitResult;")
    private HitResult playermodel$opticalPick(float delta, Entity cameraEntity, Operation<HitResult> original) {
        var client = MinecraftClient.getInstance();
        var manager = PlayerModelClient.appearanceManager();
        var model = cameraEntity == client.player ? manager.preparedFor(cameraEntity.getUuid()) : null;
        if (model == null || !model.usesModelPivots() || !manager.viewEnabled()
                || !client.gameRenderer.getCamera().isReady()) return original.call(delta, cameraEntity);
        Entity previous = CameraPickContext.ENTITY.get();
        CameraPickContext.ENTITY.set(cameraEntity);
        try {
            return original.call(delta, cameraEntity);
        } finally {
            if (previous == null) CameraPickContext.ENTITY.remove();
            else CameraPickContext.ENTITY.set(previous);
        }
    }
}
