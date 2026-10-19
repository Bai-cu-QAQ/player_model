package dev.playermodel.model;

/** One rendered face of one cube, in vanilla model space (1 unit = 1/16 block). */
public record CubeFace(FaceDir dir, int textureIndex, FaceVertex[] vertices) {
    public CubeFace {
        if (vertices == null || vertices.length != 4) {
            throw new IllegalArgumentException("A face must have exactly 4 vertices");
        }
    }
}
