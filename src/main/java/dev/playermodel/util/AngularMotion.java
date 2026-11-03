package dev.playermodel.util;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Spring state in radians in an animation-independent frame, integrated at bounded real-time steps. */
public final class AngularMotion {
    public final Vector3f angle = new Vector3f();
    public final Vector3f velocity = new Vector3f();

    public void advance(Vector3f target, float response, float dampingRatio, float air,
                        float maxRate, float maxAngle, float seconds, int subdivisions) {
        advance(target, response, dampingRatio, air, maxRate, maxAngle, null, null, seconds, subdivisions);
    }

    /** Legacy symmetric component limits. Null keeps the spherical magnitude limit. */
    public void advance(Vector3f target, float response, float dampingRatio, float air,
                        float maxRate, float maxAngle, Vector3f axisLimits, float seconds, int subdivisions) {
        if (axisLimits == null) {
            advance(target, response, dampingRatio, air, maxRate, maxAngle, null, null, seconds, subdivisions);
        } else {
            advance(target, response, dampingRatio, air, maxRate, maxAngle,
                    new Vector3f(axisLimits).negate(), axisLimits, seconds, subdivisions);
        }
    }

    /** Axis ranges are rotation-vector components in the same model-space frame as angle (radians).
     * Asymmetric [min,max] ranges are supported; null min/max retains the legacy spherical limit. */
    public void advance(Vector3f target, float response, float dampingRatio, float air,
                        float maxRate, float maxAngle, Vector3f axisMin, Vector3f axisMax,
                        float seconds, int subdivisions) {
        if (!target.isFinite() || !Float.isFinite(seconds) || seconds <= 0) return;
        if ((axisMin == null) != (axisMax == null)) return;
        if (axisMin != null && (!axisMin.isFinite() || !axisMax.isFinite()
                || axisMin.x > axisMax.x || axisMin.y > axisMax.y || axisMin.z > axisMax.z)) {
            return;
        }
        int count = Math.max(Math.max(1, subdivisions), (int) Math.ceil(seconds / (1F / 240F)));
        count = Math.min(256, count);
        float dt = seconds / count;
        if (axisMin != null) constrainAxes(axisMin, axisMax);
        for (int i = 0; i < count; i++) {
            Vector3f acceleration = new Vector3f(target).sub(angle).mul(response * response);
            float drag = 2 * dampingRatio * response + air;
            // Implicit damping: passive even with large drag or slow rendering.
            velocity.fma(dt, acceleration).div(1 + drag * dt);
            float rate = velocity.length();
            if (rate > maxRate) velocity.mul(maxRate / rate);
            angle.fma(dt, velocity);
            if (axisMin != null) {
                constrainAxes(axisMin, axisMax);
            } else {
                float magnitude = angle.length();
                if (magnitude > maxAngle && magnitude > 1e-7F) {
                    Vector3f normal = new Vector3f(angle).div(magnitude);
                    angle.set(normal).mul(maxAngle);
                    float outward = velocity.dot(normal);
                    if (outward > 0) velocity.fma(-outward, normal);
                }
            }
        }
        if (!angle.isFinite() || !velocity.isFinite()) { angle.zero(); velocity.zero(); }
    }

    private void constrainAxes(Vector3f min, Vector3f max) {
        for (int axis = 0; axis < 3; axis++) {
            float low = min.get(axis);
            float high = max.get(axis);
            if (low == high) {
                // Normalize -0.0 to +0.0; a locked asymmetric range still pins the component.
                angle.setComponent(axis, high == 0 ? 0 : high);
                velocity.setComponent(axis, 0);
                continue;
            }
            float position = Math.max(low, Math.min(high, angle.get(axis)));
            float speed = velocity.get(axis);
            angle.setComponent(axis, position);
            // At other bounds only outward velocity is removed, so reversing direction responds
            // immediately instead of sticking to the stop.
            if (position >= high && speed > 0 || position <= low && speed < 0) {
                velocity.setComponent(axis, 0);
            }
        }
    }

    /** Coordinate conjugation for a model-space rotation applied after the animated rest frame. */
    public static Quaternionf localOffset(Quaternionf restFrame, Vector3f modelAngle) {
        float length = modelAngle.length();
        Quaternionf offset = length < 1e-7F ? new Quaternionf()
                : new Quaternionf().rotationAxis(length, new Vector3f(modelAngle).div(length));
        return new Quaternionf(restFrame).invert().mul(offset).mul(restFrame).normalize();
    }

    public static Vector3f angularVelocity(Quaternionf previous, Quaternionf current, float dt) {
        Quaternionf delta = new Quaternionf(current).mul(new Quaternionf(previous).invert()).normalize();
        if (delta.w < 0) delta.mul(-1F);
        Vector3f axis = new Vector3f(delta.x, delta.y, delta.z);
        float length = axis.length();
        if (length < 1e-7F || dt <= 0) return new Vector3f();
        return axis.mul(2F * (float) Math.atan2(length, delta.w) / (length * dt));
    }
}
