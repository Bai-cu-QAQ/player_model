package dev.playermodel.model;

/** A vertex of a rendered cube face: position in model pixels + normalized texture coordinates. */
public record FaceVertex(float x, float y, float z, float u, float v) {
}
