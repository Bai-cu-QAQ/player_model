package dev.playermodel.mixin;

import dev.playermodel.util.CameraPickContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Scoped optical origin/direction used by both standard and weapon-component vanilla picking. */
@Mixin(Entity.class)
public abstract class CameraPickEntityMixin {
    @Inject(method = "getCameraPosVec", at = @At("HEAD"), cancellable = true)
    private void playermodel$cameraOrigin(float delta, CallbackInfoReturnable<Vec3d> cir) {
        if (CameraPickContext.ENTITY.get() == (Object) this) {
            cir.setReturnValue(MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos());
        }
    }
    @Inject(method = "getRotationVec(F)Lnet/minecraft/util/math/Vec3d;", at = @At("HEAD"), cancellable = true)
    private void playermodel$cameraDirection(float delta, CallbackInfoReturnable<Vec3d> cir) {
        if (CameraPickContext.ENTITY.get() == (Object) this) {
            Vector3f forward = new Vector3f(0, 0, -1).rotate(MinecraftClient.getInstance().gameRenderer.getCamera().getRotation());
            cir.setReturnValue(new Vec3d(forward.x, forward.y, forward.z));
        }
    }
    @Inject(method = "getBoundingBox", at = @At("RETURN"), cancellable = true)
    private void playermodel$includeOpticalOrigin(CallbackInfoReturnable<Box> cir) {
        if (CameraPickContext.ENTITY.get() == (Object) this) {
            Vec3d p = MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos();
            cir.setReturnValue(cir.getReturnValue().union(new Box(p, p)));
        }
    }
}
