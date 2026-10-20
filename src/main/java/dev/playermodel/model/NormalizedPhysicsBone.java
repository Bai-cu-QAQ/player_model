package dev.playermodel.model;

import java.util.List;

/** Compiled independent physics bone. Cubes still use absolute model coordinates. */
public record NormalizedPhysicsBone(String name, BoneKind parentBone, String parentPhysics,
                                    float pivotX, float pivotY, float pivotZ,
                                    List<NormalizedCube> cubes) {
    public NormalizedPhysicsBone {
        cubes = List.copyOf(cubes);
    }
}
