package dev.playermodel.jpm;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime values and part state for JPM expressions. Part properties are intentionally mutable:
 * JPM animations are evaluated in file order and later expressions can read earlier part values.
 */
public final class JpmEvalContext {
    public double age;
    public double headYaw;
    public double headPitch;
    public double limbSpeed;
    public double limbSwing;
    public double hurtTime;
    public double distance;
    public double id;
    public boolean hurt;
    public final Map<String, Double> variables = new LinkedHashMap<>();
    public final Map<String, PartState> parts = new LinkedHashMap<>();

    private final Map<Long, Double> randomCache = new LinkedHashMap<>();

    public PartState part(String name) {
        return parts.computeIfAbsent(name, ignored -> new PartState());
    }

    public double value(String name) {
        if (name == null || name.isEmpty()) return 0;
        return switch (name) {
            case "age" -> age;
            case "head_yaw" -> headYaw;
            case "head_pitch" -> headPitch;
            case "limb_speed" -> limbSpeed;
            case "limb_swing" -> limbSwing;
            case "hurt_time" -> hurtTime;
            case "distance" -> distance;
            case "id" -> id;
            case "is_hurt" -> hurt ? 1 : 0;
            case "pi" -> Math.PI;
            case "e" -> Math.E;
            case "true" -> 1;
            case "false" -> 0;
            default -> valueFromMapOrPart(name);
        };
    }

    private double valueFromMapOrPart(String name) {
        Double variable = variables.get(name);
        if (variable != null) return variable;
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            PartState state = parts.get(name.substring(0, dot));
            if (state != null) {
                return state.value(name.substring(dot + 1));
            }
        }
        return 0;
    }

    /** Deterministic random per seed, matching EMF's seeded random(id) idiom. */
    public double random(double seed) {
        long key = Double.doubleToLongBits(seed);
        Double cached = randomCache.get(key);
        if (cached != null) return cached;
        long state = key;
        state ^= state >>> 33;
        state *= 0xff51afd7ed558ccdL;
        state ^= state >>> 33;
        state *= 0xc4ceb9fe1a85ec53L;
        state ^= state >>> 33;
        double result = (state >>> 11) / (double) (1L << 53);
        if (randomCache.size() > 4096) randomCache.clear();
        randomCache.put(key, result);
        return result;
    }

    public static final class PartState {
        public double tx;
        public double ty;
        public double tz;
        public double rx;
        public double ry;
        public double rz;
        public double sx = 1;
        public double sy = 1;
        public double sz = 1;
        public boolean visible = true;

        public double value(String property) {
            return switch (property) {
                case "tx" -> tx;
                case "ty" -> ty;
                case "tz" -> tz;
                case "rx" -> rx;
                case "ry" -> ry;
                case "rz" -> rz;
                case "sx" -> sx;
                case "sy" -> sy;
                case "sz" -> sz;
                case "visible" -> visible ? 1 : 0;
                default -> 0;
            };
        }

        public void set(String property, double value) {
            switch (property) {
                case "tx" -> tx = value;
                case "ty" -> ty = value;
                case "tz" -> tz = value;
                case "rx" -> rx = value;
                case "ry" -> ry = value;
                case "rz" -> rz = value;
                case "sx" -> sx = value;
                case "sy" -> sy = value;
                case "sz" -> sz = value;
                case "visible" -> visible = value != 0;
                default -> {
                }
            }
        }
    }
}
