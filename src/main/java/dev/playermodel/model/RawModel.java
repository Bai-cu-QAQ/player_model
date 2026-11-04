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

    /** Mirrors authored left/right limb positions across the model center. */
    public void mirrorLimbPositions() {
        mirrorBone(BoneKind.LEFT_ARM);
        mirrorBone(BoneKind.RIGHT_ARM);
        mirrorBone(BoneKind.LEFT_LEG);
        mirrorBone(BoneKind.RIGHT_LEG);
        mirrorBone(BoneKind.LEFT_SLEEVE);
        mirrorBone(BoneKind.RIGHT_SLEEVE);
        mirrorBone(BoneKind.LEFT_PANTS);
        mirrorBone(BoneKind.RIGHT_PANTS);
        mirrorLimbAnchors();
    }

    /** Swaps the East/West texture faces of centered body parts after the limb mirror. */
    public void mirrorCenteredUvs() {
        mirrorUvs(BoneKind.HEAD);
        mirrorUvs(BoneKind.BODY);
        mirrorUvs(BoneKind.HAT);
        mirrorUvs(BoneKind.JACKET);
    }

    private void mirrorBone(BoneKind kind) {
        RawBone bone = bone(kind);
        if (bone == null) return;
        bone.pivot[0] = -bone.pivot[0];
        bone.rotation[1] = -bone.rotation[1];
        bone.rotation[2] = -bone.rotation[2];
        for (RawElement element : bone.elements) {
            float fromX = -element.from[0];
            float toX = -element.to[0];
            element.from[0] = Math.min(fromX, toX);
            element.to[0] = Math.max(fromX, toX);
            element.origin[0] = -element.origin[0];
            element.rotation[1] = -element.rotation[1];
            element.rotation[2] = -element.rotation[2];
            swapEastWest(element);
        }
    }

    private void mirrorUvs(BoneKind kind) {
        RawBone bone = bone(kind);
        if (bone == null) return;
        for (RawElement element : bone.elements) {
            swapEastWest(element);
        }
    }

    private static void swapEastWest(RawElement element) {
        if (element.boxUv || (!element.faces.containsKey(FaceDir.EAST)
                && !element.faces.containsKey(FaceDir.WEST))) {
            return;
        }
        java.util.EnumMap<FaceDir, RawFace> faces = new java.util.EnumMap<>(element.faces);
        RawFace east = faces.remove(FaceDir.EAST);
        RawFace west = faces.remove(FaceDir.WEST);
        if (east != null) faces.put(FaceDir.WEST, mirroredU(east));
        if (west != null) faces.put(FaceDir.EAST, mirroredU(west));
        element.faces = faces;
    }

    /** Reverses a face's horizontal UV axis when the cube is reflected across X. */
    private static RawFace mirroredU(RawFace face) {
        return new RawFace(face.u2(), face.v1(), face.u1(), face.v2(),
                face.rotation(), face.textureIndex());
    }

    private void mirrorLimbAnchors() {
        for (int i = 0; i < anchors.size(); i++) {
            Anchor anchor = anchors.get(i);
            if (Anchor.RIGHT_HAND.equals(anchor.name()) || Anchor.LEFT_HAND.equals(anchor.name())) {
                anchors.set(i, new Anchor(anchor.name(), anchor.bone(), -anchor.x(), anchor.y(),
                        anchor.z(), anchor.scale()));
            }
        }
    }
}
