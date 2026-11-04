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
    /** Optional OptiFine/EMF JPM face overlay source, relative to the model folder. */
    public String faceJpm;
    /** Optional independent PNG for the JPM face overlay. */
    public byte[] faceTexturePng;
    /** Optional JEM document used for the vanilla-part base layer. */
    public String jem;
    /** Directory used to resolve JEM referenced models. */
    public String jemDirectory;
    /** JEM integration mode: "head" (default) or "full". */
    public String jemMode;

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
}
