package dev.playermodel.model;

/** A Blockbench per-face UV definition (texture pixels). */
public record RawFace(float u1, float v1, float u2, float v2, int rotation, int textureIndex) {
}
