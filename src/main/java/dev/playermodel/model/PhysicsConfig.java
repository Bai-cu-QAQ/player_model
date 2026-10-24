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
        /** Null inherits the model default; the runtime falls back to pendulum. */
        public String mode;
        /** Movement-driven force multiplier (default 1). */
        public Float motionStrength;
        /** Acceleration-driven force multiplier (default 0.12). */
        public Float accelerationStrength;
        /** Restoring spring response per second (default 8). */
        public Float restResponse;
        /** Swing damping ratio (default 0.9). */
        public Float swingDamping;
        /** Cloth restoring-response multiplier (default 0.8). */
        public Float clothResponseScale;
        /** Angular air resistance per second (default 1.5). */
        public Float airResistance;
        /** Maximum angular speed in degrees per second (default 360). */
        public Float maxAngularSpeed;
        /** Force low-pass response per second (default 10). */
        public Float forceSmoothing;
        /** Parent rotation inertia multiplier (default 0.7). */
        public Float verticalMotionScale;
        public Float verticalAccelerationScale;
        public Float viewYawScale;
        public Float viewPitchScale;
        public Float angularAccelerationGain;
        public Float maxAngularAcceleration;
        public Float angularInertia;        /** Base trail gain per unit local speed (default 0.065). */
        public Float motionGain;
        /** Base acceleration force gain (default 0.015). */
        public Float accelerationGain;
        /** Base animation angular-velocity gain (default 0.045). */
        public Float animationGain;
        /** Base gravity torque gain (default 0.04). */
        public Float gravityGain;
        /** Hard upper bound for the local angle in degrees (default 120). */
        public Float maxAngleLimit;
        /** Hard upper bound for substeps (default 32). */
        public Integer maxSubsteps;
        /** Linear speed smoothing rate (default 10). */
        public Float speedSmoothing;
        /** Acceleration smoothing rate (default 8). */
        public Float accelerationSmoothing;
        /** Maximum sampled linear speed (default 12.5). */
        public Float maxSpeed;
        /** Maximum sampled linear acceleration (default 30). */
        public Float maxAcceleration;
        /** Maximum inherited animation angular speed (default 6). */
        public Float maxAnimationSpeed;
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
            s.motionStrength = motionStrength;
            s.accelerationStrength = accelerationStrength;
            s.restResponse = restResponse;
            s.swingDamping = swingDamping;
            s.clothResponseScale = clothResponseScale;
            s.airResistance = airResistance;
            s.maxAngularSpeed = maxAngularSpeed;
            s.forceSmoothing = forceSmoothing;
            s.verticalMotionScale = verticalMotionScale;
            s.verticalAccelerationScale = verticalAccelerationScale;
            s.viewYawScale = viewYawScale;
            s.viewPitchScale = viewPitchScale;
            s.angularAccelerationGain = angularAccelerationGain;
            s.maxAngularAcceleration = maxAngularAcceleration;
            s.angularInertia = angularInertia;            s.motionGain = motionGain;
            s.accelerationGain = accelerationGain;
            s.animationGain = animationGain;
            s.gravityGain = gravityGain;
            s.maxAngleLimit = maxAngleLimit;
            s.maxSubsteps = maxSubsteps;
            s.speedSmoothing = speedSmoothing;
            s.accelerationSmoothing = accelerationSmoothing;
            s.maxSpeed = maxSpeed;
            s.maxAcceleration = maxAcceleration;
            s.maxAnimationSpeed = maxAnimationSpeed;
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
            if (o.motionStrength != null) motionStrength = o.motionStrength;
            if (o.accelerationStrength != null) accelerationStrength = o.accelerationStrength;
            if (o.restResponse != null) restResponse = o.restResponse;
            if (o.swingDamping != null) swingDamping = o.swingDamping;
            if (o.clothResponseScale != null) clothResponseScale = o.clothResponseScale;
            if (o.airResistance != null) airResistance = o.airResistance;
            if (o.maxAngularSpeed != null) maxAngularSpeed = o.maxAngularSpeed;
            if (o.forceSmoothing != null) forceSmoothing = o.forceSmoothing;
            if (o.verticalMotionScale != null) verticalMotionScale = o.verticalMotionScale;
            if (o.verticalAccelerationScale != null) verticalAccelerationScale = o.verticalAccelerationScale;
            if (o.viewYawScale != null) viewYawScale = o.viewYawScale;
            if (o.viewPitchScale != null) viewPitchScale = o.viewPitchScale;
            if (o.angularAccelerationGain != null) angularAccelerationGain = o.angularAccelerationGain;
            if (o.maxAngularAcceleration != null) maxAngularAcceleration = o.maxAngularAcceleration;
            if (o.angularInertia != null) angularInertia = o.angularInertia;            if (o.motionGain != null) motionGain = o.motionGain;
            if (o.accelerationGain != null) accelerationGain = o.accelerationGain;
            if (o.animationGain != null) animationGain = o.animationGain;
            if (o.gravityGain != null) gravityGain = o.gravityGain;
            if (o.maxAngleLimit != null) maxAngleLimit = o.maxAngleLimit;
            if (o.maxSubsteps != null) maxSubsteps = o.maxSubsteps;
            if (o.speedSmoothing != null) speedSmoothing = o.speedSmoothing;
            if (o.accelerationSmoothing != null) accelerationSmoothing = o.accelerationSmoothing;
            if (o.maxSpeed != null) maxSpeed = o.maxSpeed;
            if (o.maxAcceleration != null) maxAcceleration = o.maxAcceleration;
            if (o.maxAnimationSpeed != null) maxAnimationSpeed = o.maxAnimationSpeed;
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

        public float motionStrength(float fallback) {
            return bounded(motionStrength, fallback, 1.0F, 0.0F, 10.0F);
        }

        public float accelerationStrength(float fallback) {
            return bounded(accelerationStrength, fallback, 0.12F, 0.0F, 10.0F);
        }

        public float restResponse(float fallback) {
            return bounded(restResponse, fallback, 8.0F, 0.1F, 40.0F);
        }

        public float swingDamping(float fallback) {
            return bounded(swingDamping, fallback, 0.9F, 0.1F, 4.0F);
        }

        public float clothResponseScale(float fallback) {
            return bounded(clothResponseScale, fallback, 0.8F, 0.1F, 2.0F);
        }

        public float airResistance(float fallback) {
            return bounded(airResistance, fallback, 1.5F, 0.0F, 30.0F);
        }

        public float maxAngularSpeed(float fallback) {
            return bounded(maxAngularSpeed, fallback, 360.0F, 1.0F, 1440.0F);
        }

        public float forceSmoothing(float fallback) {
            return bounded(forceSmoothing, fallback, 10.0F, 0.1F, 60.0F);
        }

        public float motionGain(float fallback) { return bounded(motionGain, fallback, 0.065F, 0.0F, 10.0F); }
        public float accelerationGain(float fallback) { return bounded(accelerationGain, fallback, 0.015F, 0.0F, 10.0F); }
        public float animationGain(float fallback) { return bounded(animationGain, fallback, 0.045F, 0.0F, 10.0F); }
        public float gravityGain(float fallback) { return bounded(gravityGain, fallback, 0.04F, 0.0F, 10.0F); }
        public float maxAngleLimit(float fallback) { return bounded(maxAngleLimit, fallback, 120.0F, 1.0F, 360.0F); }
        public int maxSubsteps(int fallback) { return maxSubsteps == null ? fallback : Math.max(1, maxSubsteps); }
        public float speedSmoothing(float fallback) { return bounded(speedSmoothing, fallback, 10.0F, 0.1F, 60.0F); }
        public float accelerationSmoothing(float fallback) { return bounded(accelerationSmoothing, fallback, 8.0F, 0.1F, 60.0F); }
        public float maxSpeed(float fallback) { return bounded(maxSpeed, fallback, 12.5F, 0.1F, 100.0F); }
        public float maxAcceleration(float fallback) { return bounded(maxAcceleration, fallback, 30.0F, 0.1F, 200.0F); }
        public float maxAnimationSpeed(float fallback) { return bounded(maxAnimationSpeed, fallback, 6.0F, 0.1F, 60.0F); }        public float angularInertia(float fallback) {
            return bounded(angularInertia, fallback, 0.7F, 0.0F, 2.0F);
        }

        public float verticalMotionScale(float fallback) { return bounded(verticalMotionScale, fallback, 1F, 0F, 10F); }
        public float verticalAccelerationScale(float fallback) { return bounded(verticalAccelerationScale, fallback, 1F, 0F, 10F); }
        public float viewYawScale(float fallback) { return bounded(viewYawScale, fallback, 1F, 0F, 10F); }
        public float viewPitchScale(float fallback) { return bounded(viewPitchScale, fallback, 1F, 0F, 10F); }
        public float angularAccelerationGain(float fallback) { return bounded(angularAccelerationGain, fallback, 0.004F, 0F, 1F); }
        public float maxAngularAcceleration(float fallback) { return bounded(maxAngularAcceleration, fallback, 40F, 0F, 300F); }
        private static float bounded(Float value, float fallback, float defaultValue, float min, float max) {
            float resolved = value != null && Float.isFinite(value) ? value : fallback;
            if (!Float.isFinite(resolved)) resolved = defaultValue;
            return Math.max(min, Math.min(max, resolved));
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
