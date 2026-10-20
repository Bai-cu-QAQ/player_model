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
        /** 禁用 X 轴（pitch）物理：前后/上下拖尾。仅对当前骨骼生效。 */
        public Boolean disablePitch;
        /** 禁用 Y 轴（yaw）物理：左右转向。仅对当前骨骼生效。 */
        public Boolean disableYaw;
        /** 禁用 Z 轴（roll）物理：侧向摆动。仅对当前骨骼生效。 */
        public Boolean disableRoll;

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
            s.disablePitch = disablePitch;
            s.disableYaw = disableYaw;
            s.disableRoll = disableRoll;
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
            if (o.disablePitch != null) {
                disablePitch = o.disablePitch;
            }
            if (o.disableYaw != null) {
                disableYaw = o.disableYaw;
            }
            if (o.disableRoll != null) {
                disableRoll = o.disableRoll;
            }
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

        public boolean disablePitch(boolean fallback) {
            return disablePitch == null ? fallback : disablePitch;
        }

        public boolean disableYaw(boolean fallback) {
            return disableYaw == null ? fallback : disableYaw;
        }

        public boolean disableRoll(boolean fallback) {
            return disableRoll == null ? fallback : disableRoll;
        }
    }
}
