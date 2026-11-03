package dev.playermodel.runtime;

import dev.playermodel.model.BoneKind;
import dev.playermodel.model.CubeFace;
import dev.playermodel.model.FaceDir;
import dev.playermodel.model.FaceVertex;
import dev.playermodel.model.NormalizedBone;
import dev.playermodel.model.NormalizedCube;
import net.minecraft.client.model.ModelPart;

import java.util.List;
import java.util.Map;

/**
 * The base face layer JustExpressions gets from its JEM head box: one front quad using the
 * vanilla skin face UV region. It supplies the normal skin eyes under the animated JPM overlay.
 */
public final class BaseFaceModel {
    private BaseFaceModel() {
    }

    public static ModelPart build(PreparedModel prepared) {
        NormalizedBone head = prepared.data().bone(BoneKind.HEAD);
        if (head == null || head.cubes().isEmpty()) return null;
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        for (NormalizedCube cube : head.cubes()) {
            minX = Math.min(minX, cube.minX());
            maxX = Math.max(maxX, cube.maxX());
            minY = Math.min(minY, cube.minY());
            maxY = Math.max(maxY, cube.maxY());
            minZ = Math.min(minZ, cube.minZ());
        }
        float width = prepared.data().textureWidth;
        float height = prepared.data().textureHeight;
        if (width <= 0) width = 64;
        if (height <= 0) height = 64;
        float u1 = 8 / width;
        float v1 = 8 / height;
        float u2 = 16 / width;
        float v2 = 16 / height;
        // Slight in-front offset: the JPM overlay owns the surface, this quad only fills its gaps.
        float z = minZ - 0.011F;
        FaceVertex[] vertices = {
                new FaceVertex(minX, minY, z, u1, v1),
                new FaceVertex(maxX, minY, z, u2, v1),
                new FaceVertex(maxX, maxY, z, u2, v2),
                new FaceVertex(minX, maxY, z, u1, v2)
        };
        NormalizedCube faceCube = new NormalizedCube(minX, minY, z, maxX, maxY, z,
                List.of(new CubeFace(FaceDir.NORTH, 0, vertices)));
        float[] pivot = prepared.pivot(BoneKind.HEAD);
        if (pivot == null) pivot = new float[]{0, 0, 0};
        ModelPart part = new ModelPart(PreparedModel.buildCuboids(List.of(faceCube), pivot[0], pivot[1], pivot[2]),
                Map.of());
        part.setPivot(0, 0, 0);
        return part;
    }
}
