package dev.playermodel.runtime;

import org.joml.Vector3f;

/** Persistent critically damped angular contact compliance; all quantities are radians/seconds. */
public final class ContactSpring {
    public final Vector3f position = new Vector3f();
    public final Vector3f velocity = new Vector3f();

    public void reset() {
        position.zero();
        velocity.zero();
    }

    /** Exact solution for a constant target: stable at low FPS, with no overshoot from rest. */
    public void advance(Vector3f target, float dt, float frequency) {
        if (!Float.isFinite(dt) || dt <= 0 || !target.isFinite()) return;
        float w = Math.max(0.1F, frequency);
        float decay = (float) Math.exp(-w * dt);
        Vector3f displacement = new Vector3f(position).sub(target);
        Vector3f c = new Vector3f(displacement).mul(w).add(velocity);
        position.set(displacement).fma(dt, c).mul(decay).add(target);
        velocity.fma(-w * dt, c).mul(decay);
        if (!position.isFinite() || !velocity.isFinite()) reset();
    }

    /** Minimum angular correction from a point contact, regularized near the joint pivot. */
    public static Vector3f correction(Vector3f lever, Vector3f normal, float depth) {
        Vector3f gradient = new Vector3f(lever).cross(normal);
        float denominator = gradient.lengthSquared() + 0.025F;
        return gradient.mul(Math.max(0, depth) * 0.7F / denominator);
    }
}
