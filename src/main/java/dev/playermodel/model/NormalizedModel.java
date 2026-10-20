package dev.playermodel.model;

import java.util.List;

/** Fully normalized, client-independent model data. This is what gets stored and synchronized. */
public final class NormalizedModel {
    public int formatVersion = 1;
    public String displayName = "model";
    public String textureMode = "model";
    public String pivotMode = "model";
    public boolean customAnchors;
    public boolean doubleLayer = true;
    public boolean slim;
    public int textureWidth = 64;
    public int textureHeight = 64;
    public transient byte[] texturePng;
    public List<Anchor> anchors = List.of();
    public List<NormalizedBone> bones = List.of();
    public List<NormalizedPhysicsBone> physicsBones = List.of();
    public PhysicsConfig physics = new PhysicsConfig();
    public List<String> warnings = List.of();

    public NormalizedBone bone(BoneKind kind) {
        for (NormalizedBone bone : bones) {
            if (bone.kind() == kind) {
                return bone;
            }
        }
        return null;
    }
}
