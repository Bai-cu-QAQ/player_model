package dev.playermodel.model;

import java.util.List;

/** One mapped vanilla bone of an imported model. */
public record NormalizedBone(BoneKind kind, float pivotX, float pivotY, float pivotZ,
                             List<NormalizedCube> cubes) {
    public NormalizedBone {
        cubes = List.copyOf(cubes);
    }
}
