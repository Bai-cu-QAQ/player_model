package dev.playermodel.model;

import java.util.ArrayList;
import java.util.List;

/** An independent physics bone, not one of the fourteen vanilla bones. */
public final class RawPhysicsBone {
    public final String name;
    public final BoneKind parentBone;
    public final String parentPhysics;
    public final float[] pivot;
    public final float[] rotation;
    public final List<RawElement> elements = new ArrayList<>();

    public RawPhysicsBone(String name, BoneKind parentBone, String parentPhysics, float[] pivot,
                          float[] rotation) {
        this.name = name;
        this.parentBone = parentBone;
        this.parentPhysics = parentPhysics;
        this.pivot = pivot;
        this.rotation = rotation;
    }
}
