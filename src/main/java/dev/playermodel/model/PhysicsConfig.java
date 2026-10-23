package dev.playermodel.model;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Per-model physics tuning. Numeric fields are nullable overrides so a bone can specify only the
 * values it needs; unresolved values fall back to {@link #defaultSettings}.
 */
public final class PhysicsConfig {
    public Settings defaultSettings = new Settings();
    public Map<String, Settings> bones = new LinkedHashMap<>();

    public Settings resolved(String boneName) {
        Settings base = defaultSettings == null ? new Settings() : defaultSettings;
        Settings result = base.copy();
        if (boneName != null) {
            Settings override = bones == null ? null : bones.get(boneName.toLowerCase(Locale.ROOT));
            if (override != null) {
                result.apply(override);
            }
        }
        return result;
    }

    public boolean clothRequested(String boneName) {
        return "cloth".equals(resolved(boneName).mode);
    }

    public boolean hasBoneOverride(String boneName) {
        return boneName != null && bones != null
                && bones.containsKey(boneName.toLowerCase(Locale.ROOT));
    }

    public static final class Settings {
        public String mode = "pendulum";
        public Float stiffness;
        public Float damping;
        public Float gravity;
        public Float inertia;
        public Float maxAngle;
        public Float wind;
        public Float drag;
        public Integer iterations;
        /** Physics substeps per rendered frame. */
        public Integer substeps;
        /** Base angular frequency in the damped follow spring. */
        public Float springBase;
        /** How much the damping setting contributes to the angular frequency. */
        public Float springDampingScale;
        /** Additional frequency multiplier for cloth chains. */
        public Float springClothScale;
        /** Critically damped ratio, 1.0 is critical. */
        public Float dampingRatio;
        /** Damping ratio used by cloth chains. */
        public Float clothDampingRatio;
        /** Multiplier on the speed-to-angle gain. */
        public Float speedGainScale;
        /** Speed gain multiplier for cloth chains. */
        public Float clothSpeedScale;
        /** Vertical fall distance used by the lift term; larger means weaker lift. */
        public Float liftScale;
        /** Linear angular air drag per second. */
        public Float airDrag;
        /** Multiplier for parent animation rotation impulses. */
        public Float animationForce;
        /** Low-pass response rate for the desired target angle. */
        public Float targetResponse;
        /** Parent pitch angular-velocity influence. */
        public Float pitchOmegaScale;
        /** Parent yaw angular-velocity influence. */
        public Float yawOmegaScale;
        /** Parent roll angular-velocity influence. */
        public Float rollOmegaScale;
        /** Cloth stiffness multiplier. */
        public Float clothStiffnessScale;
        /** Cloth damping multiplier. */
        public Float clothDampingScale;
        /** Cloth maximum-angle multiplier. */
        public Float clothMaxAngleScale;

        public Settings copy() {
            Settings s = new Settings();
            s.mode = mode;
            s.stiffness = stiffness;
            s.damping = damping;
            s.gravity = gravity;
            s.inertia = inertia;
            s.maxAngle = maxAngle;
            s.wind = wind;
            s.drag = drag;
            s.iterations = iterations;
            s.substeps = substeps;
            s.springBase = springBase;
            s.springDampingScale = springDampingScale;
            s.springClothScale = springClothScale;
            s.dampingRatio = dampingRatio;
            s.clothDampingRatio = clothDampingRatio;
            s.speedGainScale = speedGainScale;
            s.clothSpeedScale = clothSpeedScale;
            s.liftScale = liftScale;
            s.airDrag = airDrag;
            s.animationForce = animationForce;
            s.targetResponse = targetResponse;
            s.pitchOmegaScale = pitchOmegaScale;
            s.yawOmegaScale = yawOmegaScale;
            s.rollOmegaScale = rollOmegaScale;
            s.clothStiffnessScale = clothStiffnessScale;
            s.clothDampingScale = clothDampingScale;
            s.clothMaxAngleScale = clothMaxAngleScale;
            return s;
        }

        public void apply(Settings o) {
            if (o == null) {
                return;
            }
            if (o.mode != null) {
                mode = o.mode;
            }
            if (o.stiffness != null) {
                stiffness = o.stiffness;
            }
            if (o.damping != null) {
                damping = o.damping;
            }
            if (o.gravity != null) {
                gravity = o.gravity;
            }
            if (o.inertia != null) {
                inertia = o.inertia;
            }
            if (o.maxAngle != null) {
                maxAngle = o.maxAngle;
            }
            if (o.wind != null) {
                wind = o.wind;
            }
            if (o.drag != null) {
                drag = o.drag;
            }
            if (o.iterations != null) {
                iterations = o.iterations;
            }
            if (o.substeps != null) substeps = o.substeps;
            if (o.springBase != null) springBase = o.springBase;
            if (o.springDampingScale != null) springDampingScale = o.springDampingScale;
            if (o.springClothScale != null) springClothScale = o.springClothScale;
            if (o.dampingRatio != null) dampingRatio = o.dampingRatio;
            if (o.clothDampingRatio != null) clothDampingRatio = o.clothDampingRatio;
            if (o.speedGainScale != null) speedGainScale = o.speedGainScale;
            if (o.clothSpeedScale != null) clothSpeedScale = o.clothSpeedScale;
            if (o.liftScale != null) liftScale = o.liftScale;
            if (o.airDrag != null) airDrag = o.airDrag;
            if (o.animationForce != null) animationForce = o.animationForce;
            if (o.targetResponse != null) targetResponse = o.targetResponse;
            if (o.pitchOmegaScale != null) pitchOmegaScale = o.pitchOmegaScale;
            if (o.yawOmegaScale != null) yawOmegaScale = o.yawOmegaScale;
            if (o.rollOmegaScale != null) rollOmegaScale = o.rollOmegaScale;
            if (o.clothStiffnessScale != null) clothStiffnessScale = o.clothStiffnessScale;
            if (o.clothDampingScale != null) clothDampingScale = o.clothDampingScale;
            if (o.clothMaxAngleScale != null) clothMaxAngleScale = o.clothMaxAngleScale;
        }

        public float stiffness(float fallback) {
            return stiffness == null ? fallback : stiffness;
        }

        public float damping(float fallback) {
            return damping == null ? fallback : damping;
        }

        public float gravity(float fallback) {
            return gravity == null ? fallback : gravity;
        }

        public float inertia(float fallback) {
            return inertia == null ? fallback : inertia;
        }

        public float maxAngle(float fallback) {
            return maxAngle == null ? fallback : maxAngle;
        }

        public float wind(float fallback) {
            return wind == null ? fallback : wind;
        }

        public float drag(float fallback) {
            return drag == null ? fallback : drag;
        }

        public int iterations(int fallback) {
            return iterations == null ? fallback : iterations;
        }

        public int substeps(int fallback) { return substeps == null ? fallback : substeps; }
        public float springBase(float fallback) { return springBase == null ? fallback : springBase; }
        public float springDampingScale(float fallback) { return springDampingScale == null ? fallback : springDampingScale; }
        public float springClothScale(float fallback) { return springClothScale == null ? fallback : springClothScale; }
        public float dampingRatio(float fallback) { return dampingRatio == null ? fallback : dampingRatio; }
        public float clothDampingRatio(float fallback) { return clothDampingRatio == null ? fallback : clothDampingRatio; }
        public float speedGainScale(float fallback) { return speedGainScale == null ? fallback : speedGainScale; }
        public float clothSpeedScale(float fallback) { return clothSpeedScale == null ? fallback : clothSpeedScale; }
        public float liftScale(float fallback) { return liftScale == null ? fallback : liftScale; }
        public float airDrag(float fallback) { return airDrag == null ? fallback : airDrag; }
        public float animationForce(float fallback) { return animationForce == null ? fallback : animationForce; }
        public float targetResponse(float fallback) { return targetResponse == null ? fallback : targetResponse; }
        public float pitchOmegaScale(float fallback) { return pitchOmegaScale == null ? fallback : pitchOmegaScale; }
        public float yawOmegaScale(float fallback) { return yawOmegaScale == null ? fallback : yawOmegaScale; }
        public float rollOmegaScale(float fallback) { return rollOmegaScale == null ? fallback : rollOmegaScale; }
        public float clothStiffnessScale(float fallback) { return clothStiffnessScale == null ? fallback : clothStiffnessScale; }
        public float clothDampingScale(float fallback) { return clothDampingScale == null ? fallback : clothDampingScale; }
        public float clothMaxAngleScale(float fallback) { return clothMaxAngleScale == null ? fallback : clothMaxAngleScale; }
    }
}
