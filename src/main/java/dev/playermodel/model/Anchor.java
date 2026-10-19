package dev.playermodel.model;

/**
 * Feature attachment point from a Blockbench empty group named anchor_*. The pivot is in model
 * pixels (vanilla model space); scale is an absolute feature scale, or 0 for the automatic rig scale.
 */
public record Anchor(String name, BoneKind bone, float x, float y, float z, float scale) {
    public static final String HELMET = "helmet";
    public static final String CHEST = "chest";
    public static final String LEGS = "legs";
    public static final String BOOTS = "boots";
    public static final String RIGHT_HAND = "right_hand";
    public static final String LEFT_HAND = "left_hand";
    public static final String CAPE = "cape";
}
