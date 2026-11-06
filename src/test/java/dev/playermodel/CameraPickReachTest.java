package dev.playermodel;

import dev.playermodel.util.CameraPickContext;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CameraPickReachTest {
    @Test
    void rearCameraDistanceDoesNotConsumeBlockOrAttackReach() {
        Vec3d eye = new Vec3d(0, 1.62, 0);
        Vec3d forward = new Vec3d(0, 0, 1);
        for (double distance : new double[]{0.25, 1, 4, 8}) {
            Vec3d camera = eye.subtract(forward.multiply(distance));
            Vec3d origin = CameraPickContext.interactionOrigin(camera, forward, eye, false);
            assertVector(eye, origin);
            for (double reach : new double[]{3, 4.5, 5}) {
                assertEquals(reach, origin.add(forward.multiply(reach)).distanceTo(eye), 1e-9);
            }
        }
    }

    @Test
    void frontCameraAlsoStartsAtPlayerDepth() {
        Vec3d eye = new Vec3d(0, 1.62, 0);
        Vec3d forward = new Vec3d(0, 0, -1);
        assertVector(eye, CameraPickContext.interactionOrigin(new Vec3d(0, 1.62, 4), forward, eye, false));
    }

    @Test
    void pitchedCameraKeepsSightLineWithoutSpendingReachOnCameraOffset() {
        Vec3d eye = new Vec3d(2, 1.62, 3);
        Vec3d forward = new Vec3d(0.3, -0.4, 0.5).normalize();
        Vec3d camera = eye.subtract(forward.multiply(4));
        assertVector(eye, CameraPickContext.interactionOrigin(camera, forward, eye, false));
    }

    @Test
    void lateralOffsetStaysOnCrosshairLineAtPlayerDepth() {
        Vec3d eye = new Vec3d(0, 1.62, 0);
        Vec3d camera = new Vec3d(0.25, 1.62, -4);
        Vec3d direction = new Vec3d(0, 0, 1);
        Vec3d origin = CameraPickContext.interactionOrigin(camera, direction, eye, false);
        assertVector(new Vec3d(0.25, 1.62, 0), origin);
        assertEquals(0, eye.subtract(origin).dotProduct(direction), 1e-9);
    }

    @Test
    void firstPersonKeepsActualAdaptedCameraOrigin() {
        Vec3d camera = new Vec3d(0, 0.81, 0);
        assertSame(camera, CameraPickContext.interactionOrigin(camera, new Vec3d(0, 0, 1),
                new Vec3d(0, 1.62, 0), true));
    }

    private static void assertVector(Vec3d expected, Vec3d actual) {
        assertEquals(expected.x, actual.x, 1e-9);
        assertEquals(expected.y, actual.y, 1e-9);
        assertEquals(expected.z, actual.z, 1e-9);
    }
}
