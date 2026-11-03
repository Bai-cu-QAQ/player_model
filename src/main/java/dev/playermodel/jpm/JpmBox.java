package dev.playermodel.jpm;

import java.util.EnumMap;
import java.util.Map;
import dev.playermodel.model.FaceDir;

/** One OptiFine/EMF JPM box. Coordinates are [x, y, z, width, height, depth]. */
public final class JpmBox {
    public final float[] coordinates;
    public final Map<FaceDir, float[]> uvs;
    public final float[] textureOffset;
    public final String mirrorTexture;
    public final float sizeAdd;
    public final float sizeAddX;
    public final float sizeAddY;
    public final float sizeAddZ;

    public JpmBox(float[] coordinates, Map<FaceDir, float[]> uvs, float[] textureOffset,
                  String mirrorTexture, float sizeAdd, float sizeAddX, float sizeAddY, float sizeAddZ) {
        this.coordinates = coordinates.clone();
        this.uvs = new EnumMap<>(FaceDir.class);
        this.uvs.putAll(uvs);
        this.textureOffset = textureOffset == null ? new float[0] : textureOffset.clone();
        this.mirrorTexture = mirrorTexture == null ? "" : mirrorTexture;
        this.sizeAdd = sizeAdd;
        this.sizeAddX = sizeAddX;
        this.sizeAddY = sizeAddY;
        this.sizeAddZ = sizeAddZ;
    }

    public float x() { return coordinates[0]; }
    public float y() { return coordinates[1]; }
    public float z() { return coordinates[2]; }
    public float width() { return coordinates[3]; }
    public float height() { return coordinates[4]; }
    public float depth() { return coordinates[5]; }

    private boolean autoUv() {
        return textureOffset.length == 2;
    }

    public float[] uv(FaceDir dir) {
        if (autoUv()) {
            return autoUv(dir);
        }
        float[] uv = uvs.get(dir);
        return uv == null ? null : uv.clone();
    }

    private float[] autoUv(FaceDir dir) {
        float u = textureOffset[0];
        float v = textureOffset[1];
        float w = width();
        float h = height();
        float d = depth();
        float[] uv = switch (dir) {
            case DOWN -> new float[]{u + d, v, u + d + w, v + d};
            case UP -> new float[]{u + d + w, v, u + d + w + w, v + d};
            case NORTH -> new float[]{u + d, v + d, u + d + w, v + d + h};
            case SOUTH -> new float[]{u + d + w + d, v + d, u + d + w + d + w, v + d + h};
            case WEST -> new float[]{u, v + d, u + d, v + d + h};
            case EAST -> new float[]{u + d + w, v + d, u + d + w + d, v + d + h};
        };
        // EMF ignores mirrorTexture in explicit per-face mode, but applies it to auto UVs.
        if (mirrorTexture.contains("u")) {
            float tmp = uv[0]; uv[0] = uv[2]; uv[2] = tmp;
        }
        if (mirrorTexture.contains("v")) {
            float tmp = uv[1]; uv[1] = uv[3]; uv[3] = tmp;
        }
        return uv;
    }
}
