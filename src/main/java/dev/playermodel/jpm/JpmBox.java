package dev.playermodel.jpm;

import java.util.EnumMap;
import java.util.Map;
import dev.playermodel.model.FaceDir;

/** One OptiFine/EMF JPM box. Coordinates are [x, y, z, width, height, depth]. */
public final class JpmBox {
    public final float[] coordinates;
    public final Map<FaceDir, float[]> uvs;
    public final float sizeAdd;
    public final float sizeAddX;
    public final float sizeAddY;
    public final float sizeAddZ;

    public JpmBox(float[] coordinates, Map<FaceDir, float[]> uvs,
                  float sizeAdd, float sizeAddX, float sizeAddY, float sizeAddZ) {
        this.coordinates = coordinates.clone();
        this.uvs = new EnumMap<>(FaceDir.class);
        this.uvs.putAll(uvs);
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

    public float[] uv(FaceDir dir) {
        float[] uv = uvs.get(dir);
        return uv == null ? null : uv.clone();
    }
}
