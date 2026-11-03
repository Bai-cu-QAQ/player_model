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
     * Rebinds the imported limb geometry to the opposite vanilla limb while preserving each
     * group's authored pivot and elements. Intended for models authored with reversed left/right
     * limb names.
     */
    public void swapLimbs() {
        for (int i = 0; i < bones.size(); i++) {
            RawBone source = bones.get(i);
            BoneKind target = swappedLimb(source.kind);
            if (target != source.kind) {
                RawBone swapped = new RawBone(target, source.pivot.clone(), source.rotation.clone());
                swapped.elements.addAll(source.elements);
                bones.set(i, swapped);
            }
        }
        for (int i = 0; i < physicsBones.size(); i++) {
            RawPhysicsBone source = physicsBones.get(i);
            BoneKind parent = swappedLimb(source.parentBone);
            if (parent != source.parentBone) {
                RawPhysicsBone swapped = new RawPhysicsBone(source.name, parent, source.parentPhysics,
                        source.pivot, source.rotation);
                swapped.elements.addAll(source.elements);
                physicsBones.set(i, swapped);
            }
        }
        warnings.add("已交换左右手臂/腿部骨骼绑定");
    }

    private static BoneKind swappedLimb(BoneKind kind) {
        if (kind == null) return null;
        return switch (kind) {
            case RIGHT_ARM -> BoneKind.LEFT_ARM;
            case LEFT_ARM -> BoneKind.RIGHT_ARM;
            case RIGHT_LEG -> BoneKind.LEFT_LEG;
            case LEFT_LEG -> BoneKind.RIGHT_LEG;
            case RIGHT_SLEEVE -> BoneKind.LEFT_SLEEVE;
            case LEFT_SLEEVE -> BoneKind.RIGHT_SLEEVE;
            case RIGHT_PANTS -> BoneKind.LEFT_PANTS;
            case LEFT_PANTS -> BoneKind.RIGHT_PANTS;
            default -> kind;
        };
    }
}
