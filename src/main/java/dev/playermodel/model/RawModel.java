package dev.playermodel.model;

import java.util.List;

/** A parsed .bbmodel, with bones already mapped and coordinates normalized to vanilla model space. */
public final class RawModel {
    public String displayName;
    public String sourceFormat;
    public int textureWidth;
    public int textureHeight;
    public RawTexture texture;
    public final java.util.List<RawTexture> textures = new java.util.ArrayList<>();
    public final List<RawBone> bones = new java.util.ArrayList<>();
    public final List<RawPhysicsBone> physicsBones = new java.util.ArrayList<>();
    public final List<String> warnings = new java.util.ArrayList<>();
    public final List<Anchor> anchors = new java.util.ArrayList<>();
    public int selectedTextureIndex;
    public int textureCount;
    public boolean slim;
    /** Optional JPM face overlay source, relative to the model folder. */
    public String faceJpm;
    /** Optional independent PNG used by the JPM face overlay. */
    public byte[] faceTexturePng;

    public RawTexture textureAt(int index) {
        if (index >= 0 && index < textures.size()) {
            return textures.get(index);
        }
        return texture;
    }

    public RawBone bone(BoneKind kind) {
        for (RawBone b : bones) {
            if (b.kind == kind) {
                return b;
            }
        }
        return null;
    }

    /**
     * Swaps the complete left/right limb binding: pivot, rotation and cubes move together. This is
     * the correct operation when a model's authored left/right limb groups are named in reverse.
     */
    public void swapLimbs() {
        swapBones(BoneKind.LEFT_ARM, BoneKind.RIGHT_ARM);
        swapBones(BoneKind.LEFT_LEG, BoneKind.RIGHT_LEG);
        swapBones(BoneKind.LEFT_SLEEVE, BoneKind.RIGHT_SLEEVE);
        swapBones(BoneKind.LEFT_PANTS, BoneKind.RIGHT_PANTS);
        swapAnchorPositions();
        warnings.add("已交换左右手臂/腿部骨骼绑定");
    }

    private void swapBones(BoneKind leftKind, BoneKind rightKind) {
        int leftIndex = indexOf(leftKind);
        int rightIndex = indexOf(rightKind);
        if (leftIndex < 0 || rightIndex < 0) return;
        RawBone left = bones.get(leftIndex);
        RawBone right = bones.get(rightIndex);
        RawBone newLeft = new RawBone(leftKind, right.pivot.clone(), right.rotation.clone());
        newLeft.elements.addAll(right.elements);
        RawBone newRight = new RawBone(rightKind, left.pivot.clone(), left.rotation.clone());
        newRight.elements.addAll(left.elements);
        bones.set(leftIndex, newLeft);
        bones.set(rightIndex, newRight);
    }

    private int indexOf(BoneKind kind) {
        for (int i = 0; i < bones.size(); i++) {
            if (bones.get(i).kind == kind) return i;
        }
        return -1;
    }

    private void swapAnchorPositions() {
        Anchor right = null;
        Anchor left = null;
        int rightIndex = -1;
        int leftIndex = -1;
        for (int i = 0; i < anchors.size(); i++) {
            Anchor anchor = anchors.get(i);
            if (Anchor.RIGHT_HAND.equals(anchor.name())) {
                right = anchor;
                rightIndex = i;
            } else if (Anchor.LEFT_HAND.equals(anchor.name())) {
                left = anchor;
                leftIndex = i;
            }
        }
        if (right == null || left == null) return;
        anchors.set(rightIndex, new Anchor(right.name(), right.bone(), left.x(), left.y(), left.z(), left.scale()));
        anchors.set(leftIndex, new Anchor(left.name(), left.bone(), right.x(), right.y(), right.z(), right.scale()));
    }
}
