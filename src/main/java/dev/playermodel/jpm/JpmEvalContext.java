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
    public double camera;
    public double frame;
    public double tick;
    public double time;
    public double health;
    public double air;
    public double id;
    public boolean hurt;
    public boolean isSneaking;
    public boolean isSprinting;
    public boolean isSwimming;
    public boolean isOnGround;
    public boolean isRiding;
    public boolean isChild;
    public boolean isInvisible;
    /** Part currently being evaluated, used by the EMF {@code this} selector. */
    public String currentPart;
    public final Map<String, Double> variables = new LinkedHashMap<>();
    public final Map<String, PartState> parts = new LinkedHashMap<>();

    private final Map<Long, Double> randomCache = new LinkedHashMap<>();

    public PartState part(String name) {
        PartState resolved = resolvePart(name);
        if (resolved != null) return resolved;
        return parts.computeIfAbsent(name, ignored -> new PartState());
    }

    /** EMF ids can be hierarchical (head.eyes.r_eye1); fall back to the unique leaf id. */
    private PartState resolvePart(String name) {
        if (name == null || name.isEmpty()) return null;
        PartState exact = parts.get(name);
        if (exact != null) return exact;
        if (name.indexOf('.') < 0) return null;
        String leaf = name.substring(name.lastIndexOf('.') + 1);
        PartState match = parts.get(leaf);
        if (match != null) return match;
        for (var entry : parts.entrySet()) {
            if (entry.getKey().endsWith("." + leaf)) return entry.getValue();
        }
        return null;
    }

    public double value(String name) {
        if (name == null || name.isEmpty()) return 0;
        if (name.equals("this")) return 0;
        if (name.startsWith("var.")) {
            Double variable = variables.get(name);
            return variable == null ? 0 : variable;
        }
        if (name.startsWith("part.")) {
            return partSelector(name.substring("part.".length()));
        }
        if (name.startsWith("this.")) {
            return currentPart == null ? 0 : partValue(currentPart, name.substring("this.".length()));
        }
        if (name.startsWith("player.")) {
            return value(name.substring("player.".length()));
        }
        if (name.startsWith("entity.")) {
            return value(name.substring("entity.".length()));
        }
        if (name.startsWith("render.")) {
            return renderValue(name.substring("render.".length()));
        }
        return switch (name) {
            case "age" -> age;
            case "head_yaw", "headYaw" -> headYaw;
            case "head_pitch", "headPitch" -> headPitch;
            case "limb_speed", "limbSpeed" -> limbSpeed;
            case "limb_swing", "limbSwing" -> limbSwing;
            case "hurt_time", "hurtTime" -> hurtTime;
            case "distance" -> distance;
            case "camera" -> camera;
            case "frame" -> frame;
            case "tick" -> tick;
            case "time" -> time;
            case "health" -> health;
            case "air" -> air;
            case "id" -> id;
            case "is_hurt", "isHurt", "hurt" -> truth(hurt);
            case "is_sneaking", "isSneaking", "sneaking" -> truth(isSneaking);
            case "is_sprinting", "isSprinting", "sprinting" -> truth(isSprinting);
            case "is_swimming", "isSwimming", "swimming" -> truth(isSwimming);
            case "is_on_ground", "isOnGround", "on_ground", "onGround" -> truth(isOnGround);
            case "is_riding", "isRiding", "riding" -> truth(isRiding);
            case "is_child", "isChild", "child" -> truth(isChild);
            case "is_invisible", "isInvisible", "invisible" -> truth(isInvisible);
            case "pi" -> Math.PI;
            case "e" -> Math.E;
            case "true" -> 1;
            case "false" -> 0;
            default -> valueFromMapOrPart(name);
        };
    }

    private double partSelector(String selector) {
        if (selector.isEmpty()) return 0;
        int dot = selector.lastIndexOf('.');
        if (dot <= 0 || dot == selector.length() - 1) return 0;
        return partValue(selector.substring(0, dot), selector.substring(dot + 1));
    }

    private double partValue(String partId, String property) {
        PartState state = resolvePart(partId);
        return state == null ? 0 : state.value(property);
    }

    private double renderValue(String property) {
        return switch (property) {
            case "age" -> age;
            case "distance" -> distance;
            case "camera", "camera_distance", "cameraDistance" -> camera;
            case "frame" -> frame;
            case "tick" -> tick;
            case "time" -> time;
            case "id" -> id;
            case "health" -> health;
            case "air" -> air;
            case "is_sneaking", "isSneaking" -> truth(isSneaking);
            case "is_sprinting", "isSprinting" -> truth(isSprinting);
            case "is_swimming", "isSwimming" -> truth(isSwimming);
            case "is_on_ground", "isOnGround" -> truth(isOnGround);
            case "is_invisible", "isInvisible" -> truth(isInvisible);
            default -> 0;
        };
    }

    private double valueFromMapOrPart(String name) {
        Double variable = variables.get(name);
        if (variable != null) return variable;
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            return partValue(name.substring(0, dot), name.substring(dot + 1));
        }
        return 0;
    }

    private static double truth(boolean value) {
        return value ? 1 : 0;
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
        /** EMF "visible_boxes": whether the boxes of this part are rendered. */
        public boolean visibleBoxes = true;

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
                case "visible_boxes", "visibleBoxes" -> visibleBoxes ? 1 : 0;
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
                case "visible_boxes", "visibleBoxes" -> visibleBoxes = value != 0;
                default -> {
                }
            }
        }
    }
}
