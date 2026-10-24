package dev.playermodel.util;

import org.joml.Vector3f;

/** Pure model-space force math. Model coordinates use positive Y downward. */
public final class SecondaryForces {
    private SecondaryForces() { }

    /** Never mutate the lever: movement and gravity must use the same geometric lever. */
    public static Vector3f torque(Vector3f lever, Vector3f force) {
        float radius = lever.length();
        return radius < 1e-5F ? new Vector3f()
                : new Vector3f(lever).cross(force).div(radius);
    }

    public static Vector3f movement(Vector3f lever, Vector3f speed, Vector3f acceleration,
                                    float speedGain, float accelerationGain,
                                    float verticalSpeedScale, float verticalAccelerationScale) {
        Vector3f force = new Vector3f(speed).mul(-speedGain);
        force.y *= verticalSpeedScale;
        Vector3f inertia = new Vector3f(acceleration).mul(-accelerationGain);
        inertia.y *= verticalAccelerationScale;
        return torque(lever, force.add(inertia));
    }

    public static Vector3f rotation(Vector3f omega, Vector3f angularAcceleration,
                                    float gain, float accelerationGain, float yawScale, float pitchScale) {
        Vector3f result = new Vector3f(omega).mul(-gain)
                .fma(-accelerationGain, angularAcceleration);
        result.x *= pitchScale;
        result.y *= yawScale;
        return result;
    }
}
