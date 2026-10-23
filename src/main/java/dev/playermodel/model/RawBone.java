package dev.playermodel.model;

import java.util.ArrayList;
import java.util.List;

/** Cubes assigned to one vanilla bone by the Blockbench importer. */
public final class RawBone {
    public final BoneKind kind;
    public float[] pivot;
    public float[] rotation = {0, 0, 0};
    public final List<RawElement> elements = new ArrayList<>();

    public RawBone(BoneKind kind, float[] pivot, float[] rotation) {
        this.kind = kind;
        this.pivot = pivot;
        this.rotation = rotation;
    }
}
