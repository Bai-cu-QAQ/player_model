package dev.playermodel.model;

import java.util.ArrayList;
import java.util.List;

/** Cubes assigned to one vanilla bone by the Blockbench importer. */
public final class RawBone {
    public final BoneKind kind;
    public float[] pivot;
    public final List<RawElement> elements = new ArrayList<>();

    public RawBone(BoneKind kind, float[] pivot) {
        this.kind = kind;
        this.pivot = pivot;
    }
}
