package dev.playermodel.runtime;

import org.joml.Vector3f;

/**
 * Persistent first-order angular contact offset. It is deliberately not a spring: the contact
 * target can move every frame, and a velocity integrator turns that into visible chatter.
 */
public final class ContactSpring {
    public final Vector3f position = new Vector3f();
    public final Vector3f velocity = new Vector3f();

    public void reset() {
        position.zero();
        velocity.zero();
    }

    /** Frame-rate independent exponential approach; zero overshoot for constant or moving targets. */
    public void advance(Vector3f target, float dt, float rate) {
        if (!Float.isFinite(dt) || dt <= 0 || !target.isFinite()) return;
        float decay = (float) Math.exp(-Math.max(0.1F, rate) * dt);
        position.sub(target).mul(decay).add(target);
        velocity.zero();
        if (!position.isFinite()) reset();
    }

    /** Minimum angular correction from a point contact, regularized near the joint pivot. */
    public static Vector3f correction(Vector3f lever, Vector3f normal, float depth) {
        Vector3f gradient = new Vector3f(lever).cross(normal);
        float denominator = gradient.lengthSquared() + 0.025F;
        return gradient.mul(Math.max(0, depth) * 0.7F / denominator);
    }
}
