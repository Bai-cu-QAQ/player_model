package dev.playermodel.mixin;

import dev.playermodel.util.CameraPickContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Scoped optical origin/direction used by vanilla picking. */
@Mixin(Entity.class)
public abstract class CameraPickEntityMixin {
    @Inject(method = "getCameraPosVec", at = @At("HEAD"), cancellable = true)
    private void playermodel$cameraOrigin(float delta, CallbackInfoReturnable<Vec3d> cir) {
        CameraPickContext.Pick pick = CameraPickContext.CURRENT.get();
        if (pick != null && pick.entity() == (Object) this) {
            cir.setReturnValue(pick.origin());
        }
    }

    @Inject(method = "getRotationVec(F)Lnet/minecraft/util/math/Vec3d;", at = @At("HEAD"), cancellable = true)
    private void playermodel$cameraDirection(float delta, CallbackInfoReturnable<Vec3d> cir) {
        CameraPickContext.Pick pick = CameraPickContext.CURRENT.get();
        if (pick != null && pick.entity() == (Object) this) {
            cir.setReturnValue(pick.direction());
        }
    }

    @Inject(method = "getBoundingBox", at = @At("RETURN"), cancellable = true)
    private void playermodel$includeOpticalOrigin(CallbackInfoReturnable<Box> cir) {
        CameraPickContext.Pick pick = CameraPickContext.CURRENT.get();
        if (pick != null && pick.entity() == (Object) this) {
            cir.setReturnValue(cir.getReturnValue().union(new Box(pick.origin(), pick.origin())));
        }
    }
}
