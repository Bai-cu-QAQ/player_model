package dev.playermodel.runtime;

import dev.playermodel.compile.ModelCompiler;
import dev.playermodel.model.BoneKind;
import dev.playermodel.model.CubeFace;
import dev.playermodel.model.FaceVertex;
import dev.playermodel.model.NormalizedBone;
import dev.playermodel.model.NormalizedCube;
import dev.playermodel.model.NormalizedModel;
import net.minecraft.client.model.ModelPart;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * A compiled, immutable model ready to be installed on the shared player model parts.
 * Cuboids are built once per pivot mode; installing only swaps list references and pivot numbers.
 */
public final class PreparedModel {
    private static final EnumSet<Direction> ALL_FACES = EnumSet.allOf(Direction.class);

    private final NormalizedModel data;
    private final Map<BoneKind, List<ModelPart.Cuboid>> modelPivoted = new EnumMap<>(BoneKind.class);
    private final Map<BoneKind, float[]> modelPivots = new EnumMap<>(BoneKind.class);
    private final List<VanillaVariant> vanillaVariants = new ArrayList<>();

    public PreparedModel(NormalizedModel data) {
        this.data = data;
        for (NormalizedBone bone : data.bones) {
            modelPivots.put(bone.kind(), new float[]{bone.pivotX(), bone.pivotY(), bone.pivotZ()});
            modelPivoted.put(bone.kind(), buildCuboids(bone, bone.pivotX(), bone.pivotY(), bone.pivotZ()));
        }
    }

    public NormalizedModel data() {
        return data;
    }

    public boolean usesModelPivots() {
        return !"vanilla".equalsIgnoreCase(data.pivotMode);
    }

    public dev.playermodel.model.Anchor anchor(String name) {
        if (data.anchors == null || name == null) {
            return null;
        }
        for (dev.playermodel.model.Anchor anchor : data.anchors) {
            if (name.equalsIgnoreCase(anchor.name())) {
                return anchor;
            }
        }
        return null;
    }

    public boolean customAnchors() {
        return data.customAnchors;
    }

    public boolean doubleLayer() {
        return data.doubleLayer;
    }

    public boolean slim() {
        return data.slim;
    }

    public String textureMode() {
        return data.textureMode == null ? "model" : data.textureMode;
    }

    public float[] pivot(BoneKind kind) {
        float[] pivot = modelPivots.get(kind);
        return pivot == null ? null : pivot.clone();
    }

    /** Approximate uniform rig scale, derived from the leg pivot height above the feet. */
    public float rigScale() {
        float[] leg = modelPivots.get(BoneKind.RIGHT_LEG);
        if (leg == null) {
            leg = modelPivots.get(BoneKind.LEFT_LEG);
        }
        if (leg == null) {
            return 1.0F;
        }
        float scale = (24.0F - leg[1]) / 12.0F;
        return Math.max(0.2F, Math.min(2.0F, scale));
    }

    public Map<BoneKind, List<ModelPart.Cuboid>> cuboids(boolean modelPivots, Map<BoneKind, float[]> basePivots) {
        if (modelPivots) {
            return modelPivoted;
        }
        synchronized (vanillaVariants) {
            for (VanillaVariant variant : vanillaVariants) {
                if (matches(variant.pivots, basePivots)) {
                    return variant.cuboids;
                }
            }
            Map<BoneKind, List<ModelPart.Cuboid>> built = new EnumMap<>(BoneKind.class);
            for (NormalizedBone bone : data.bones) {
                float[] pivot = basePivots.get(bone.kind());
                if (pivot == null) {
                    pivot = new float[]{bone.pivotX(), bone.pivotY(), bone.pivotZ()};
                }
                built.put(bone.kind(), buildCuboids(bone, pivot[0], pivot[1], pivot[2]));
            }
            vanillaVariants.add(new VanillaVariant(copy(basePivots), built));
            if (vanillaVariants.size() > 4) {
                vanillaVariants.remove(0);
            }
            return built;
        }
    }

    private static boolean matches(Map<BoneKind, float[]> a, Map<BoneKind, float[]> b) {
        for (BoneKind kind : BoneKind.VALUES) {
            float[] av = a.get(kind);
            float[] bv = b.get(kind);
            float ax = av == null ? 0 : av[0];
            float ay = av == null ? 0 : av[1];
            float az = av == null ? 0 : av[2];
            float bx = bv == null ? 0 : bv[0];
            float by = bv == null ? 0 : bv[1];
            float bz = bv == null ? 0 : bv[2];
            if (Math.abs(ax - bx) > 1e-4F || Math.abs(ay - by) > 1e-4F || Math.abs(az - bz) > 1e-4F) {
                return false;
            }
        }
        return true;
    }

    private static Map<BoneKind, float[]> copy(Map<BoneKind, float[]> source) {
        Map<BoneKind, float[]> copy = new EnumMap<>(BoneKind.class);
        source.forEach((kind, pivot) -> copy.put(kind, pivot.clone()));
        return copy;
    }

    private static List<ModelPart.Cuboid> buildCuboids(NormalizedBone bone, float px, float py, float pz) {
        List<ModelPart.Cuboid> result = new ArrayList<>(bone.cubes().size());
        for (NormalizedCube cube : bone.cubes()) {
            float minX = cube.minX() - px;
            float minY = cube.minY() - py;
            float minZ = cube.minZ() - pz;
            float maxX = cube.maxX() - px;
            float maxY = cube.maxY() - py;
            float maxZ = cube.maxZ() - pz;
            float sx = Math.max(1e-4F, maxX - minX);
            float sy = Math.max(1e-4F, maxY - minY);
            float sz = Math.max(1e-4F, maxZ - minZ);
            List<ModelPart.Quad> quads = new ArrayList<>(cube.faces().size());
            for (CubeFace face : cube.faces()) {
                ModelPart.Vertex[] vertices = new ModelPart.Vertex[4];
                FaceVertex[] source = face.vertices();
                for (int i = 0; i < 4; i++) {
                    FaceVertex v = source[i];
                    vertices[i] = new ModelPart.Vertex(v.x() - px, v.y() - py, v.z() - pz, 0.0F, 0.0F);
                }
                ModelPart.Quad quad = new ModelPart.Quad(vertices, 0, 0, 1, 1, 1, 1, false,
                        ModelCompiler.direction(face.dir()));
                // Quad's constructor rewrites the UVs of the passed vertices; replace them with
                // the exact per-face UVs from the imported model.
                for (int i = 0; i < 4; i++) {
                    FaceVertex v = source[i];
                    quad.vertices[i] = new ModelPart.Vertex(v.x() - px, v.y() - py, v.z() - pz, v.u(), v.v());
                }
                quads.add(quad);
            }
            result.add(new UvCuboid(minX, minY, minZ, sx, sy, sz, quads.toArray(new ModelPart.Quad[0])));
        }
        return List.copyOf(result);
    }

    private record VanillaVariant(Map<BoneKind, float[]> pivots, Map<BoneKind, List<ModelPart.Cuboid>> cuboids) {
    }
}
