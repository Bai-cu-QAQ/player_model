package dev.playermodel.util;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

/** Thread-local only during vanilla crosshair picking, never changes general entity eyes. */
public final class CameraPickContext {
    public static final ThreadLocal<Pick> CURRENT = new ThreadLocal<>();

    private CameraPickContext() {}

    public record Pick(Entity entity, Vec3d origin, Vec3d direction) {}

    public static Pick create(Entity entity, Vec3d cameraPosition, Quaternionfc cameraRotation,
                              Vec3d playerEye, boolean firstPerson) {
        Vector3f forward = new Vector3f(0, 0, -1).rotate(cameraRotation);
        Vec3d direction = new Vec3d(forward.x, forward.y, forward.z).normalize();
        return new Pick(entity, interactionOrigin(cameraPosition, direction, playerEye, firstPerson), direction);
    }

    public static Vec3d interactionOrigin(Vec3d cameraPosition, Vec3d direction,
                                         Vec3d playerEye, boolean firstPerson) {
        if (firstPerson) return cameraPosition;
        double lengthSquared = direction.lengthSquared();
        if (lengthSquared < 1.0E-12) return playerEye;
        double offset = playerEye.subtract(cameraPosition).dotProduct(direction) / lengthSquared;
        return cameraPosition.add(direction.multiply(offset));
    }
}
