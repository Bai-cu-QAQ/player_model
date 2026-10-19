package dev.playermodel.model;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The fourteen vanilla player model bones. Imported Blockbench groups are mapped onto these by
 * normalized name; no additional bones are ever added to the skeleton.
 */
public enum BoneKind {
    HEAD("head"),
    HAT("hat"),
    BODY("body"),
    RIGHT_ARM("right_arm"),
    LEFT_ARM("left_arm"),
    RIGHT_LEG("right_leg"),
    LEFT_LEG("left_leg"),
    RIGHT_SLEEVE("right_sleeve"),
    LEFT_SLEEVE("left_sleeve"),
    RIGHT_PANTS("right_pants"),
    LEFT_PANTS("left_pants"),
    JACKET("jacket"),
    CLOAK("cloak"),
    EAR("ear");

    public static final BoneKind[] VALUES = values();

    private static final Map<String, BoneKind> ALIASES = new LinkedHashMap<>();

    static {
        for (BoneKind kind : VALUES) {
            register(kind, kind.modelName);
        }
        // Common Blockbench / human aliases.
        register(HEAD, "skull", "头部", "头");
        register(HAT, "headwear", "head_wear", "head_layer", "hat_layer", "帽子", "头饰");
        register(BODY, "torso", "chest", "身体", "躯干");
        register(RIGHT_ARM, "rightarm", "arm_right", "rarm", "right arm", "右臂", "右手臂", "右胳膊");
        register(LEFT_ARM, "leftarm", "arm_left", "larm", "left arm", "左臂", "左手臂", "左胳膊");
        register(RIGHT_LEG, "rightleg", "leg_right", "rleg", "right leg", "右腿");
        register(LEFT_LEG, "leftleg", "leg_left", "lleg", "left leg", "左腿");
        register(RIGHT_SLEEVE, "rightsleeve", "sleeve_right", "rsleeve", "右袖", "右袖子");
        register(LEFT_SLEEVE, "leftsleeve", "sleeve_left", "lsleeve", "左袖", "左袖子");
        register(RIGHT_PANTS, "rightpants", "pants_right", "rpants", "右裤腿", "右裤");
        register(LEFT_PANTS, "leftpants", "pants_left", "lpants", "左裤腿", "左裤");
        register(JACKET, "coat", "外套", "夹克");
        register(CLOAK, "cape", "披风", "斗篷");
        register(EAR, "ears", "耳朵");
    }

    private static void register(BoneKind kind, String... aliases) {
        for (String alias : aliases) {
            ALIASES.putIfAbsent(normalize(alias), kind);
        }
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(s.length());
        boolean lastUnderscore = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean word = Character.isLetterOrDigit(c) || c == '_';
            if (word) {
                sb.append(c);
                lastUnderscore = c == '_';
            } else if (!lastUnderscore) {
                sb.append('_');
                lastUnderscore = true;
            }
        }
        int start = 0;
        int end = sb.length();
        while (start < end && sb.charAt(start) == '_') {
            start++;
        }
        while (end > start && sb.charAt(end - 1) == '_') {
            end--;
        }
        return sb.substring(start, end);
    }

    public static BoneKind byName(String raw) {
        String key = normalize(raw);
        if (key.isEmpty()) {
            return null;
        }
        BoneKind direct = ALIASES.get(key);
        if (direct != null) {
            return direct;
        }
        // camelCase without separators ("RightArm" -> "rightarm") is already normalized to rightarm,
        // which is registered as an alias above.
        return null;
    }

    public static boolean isContainerName(String raw) {
        String key = normalize(raw);
        return key.equals("root") || key.equals("armature") || key.equals("model")
                || key.equals("player") || key.equals("player_model") || key.equals("skeleton")
                || key.equals("group") || key.equals("模型") || key.equals("根") || key.equals("玩家");
    }

    private final String modelName;

    BoneKind(String modelName) {
        this.modelName = modelName;
    }

    public String modelName() {
        return modelName;
    }
}
