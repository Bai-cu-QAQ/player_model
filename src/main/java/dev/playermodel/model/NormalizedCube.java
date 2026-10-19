package dev.playermodel.model;

import java.util.List;

/** A cube in vanilla model space, with final inflated bounds and final per-face UVs. */
public record NormalizedCube(float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
                             List<CubeFace> faces) {
    public NormalizedCube {
        faces = List.copyOf(faces);
    }
}
