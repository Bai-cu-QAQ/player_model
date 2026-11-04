package dev.playermodel.runtime;

import dev.playermodel.jpm.JpmBox;
import dev.playermodel.jpm.JpmEvalContext;
import dev.playermodel.jpm.JpmModel;
import dev.playermodel.jpm.JpmPart;
import dev.playermodel.model.CubeFace;
import dev.playermodel.model.FaceDir;
import dev.playermodel.model.FaceVertex;
import dev.playermodel.model.NormalizedCube;
import net.minecraft.client.model.ModelPart;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JPM face tree built directly from the resource-pack model.
 *
 * <p>JPM boxes are local to their part. The part's {@code translate} is applied as the part origin
 * (exactly like EMF's {@code EMFModelPartCustom}); it must not be subtracted again from the box.</p>
 */
public final class JpmFaceModel {
    private final JpmModel data;
    private final ModelPart root;
    private final Map<String, ModelPart> parts = new LinkedHashMap<>();
    private final Map<String, List<ModelPart.Cuboid>> baseCuboids = new LinkedHashMap<>();

    public JpmFaceModel(JpmModel data) {
        this.data = data;
        Map<String, ModelPart> children = new LinkedHashMap<>();
        for (JpmPart part : data.roots) {
            children.put(part.id, build(part));
        }
        this.root = new ModelPart(List.of(), children);
    }

    public ModelPart root() {
        return root;
    }

    public void animate(JpmEvalContext context) {
        data.animate(context);
        apply(context);
    }

    public void apply(JpmEvalContext context) {
        for (JpmPart part : data.flatten()) {
            ModelPart target = parts.get(part.id);
            if (target == null) continue;
            JpmEvalContext.PartState state = context.parts.get(part.id);
            if (state == null) continue;
            // EMF writes animation values as absolute part-space values.
            target.setOrigin((float) state.tx, (float) state.ty, (float) state.tz);
            target.pitch = (float) state.rx;
            target.yaw = (float) state.ry;
            target.roll = (float) state.rz;
            target.xScale = (float) state.sx;
            target.yScale = (float) state.sy;
            target.zScale = (float) state.sz;
            target.visible = state.visible;
            target.cuboids = state.visibleBoxes
                    ? baseCuboids.getOrDefault(part.id, target.cuboids) : List.of();
        }
    }

    private ModelPart build(JpmPart part) {
        List<NormalizedCube> cubes = new ArrayList<>();
        for (JpmBox box : part.boxes) {
            cubes.add(toCube(box));
        }
        // JPM boxes are already local to the part; do not subtract translate again.
        List<ModelPart.Cuboid> cuboids = PreparedModel.buildCuboids(cubes, 0, 0, 0);
        Map<String, ModelPart> children = new LinkedHashMap<>();
        for (JpmPart child : part.children) {
            children.put(child.id, build(child));
        }
        ModelPart modelPart = new ModelPart(cuboids, children);
        modelPart.setOrigin(part.translate[0], part.translate[1], part.translate[2]);
        modelPart.pitch = part.rotate[0];
        modelPart.yaw = part.rotate[1];
        modelPart.roll = part.rotate[2];
        modelPart.xScale = part.scale;
        modelPart.yScale = part.scale;
        modelPart.zScale = part.scale;
        modelPart.visible = true;
        baseCuboids.put(part.id, cuboids);
        parts.put(part.id, modelPart);
        return modelPart;
    }

    private NormalizedCube toCube(JpmBox box) {
        float minX = box.x();
        float minY = box.y();
        float minZ = box.z();
        float maxX = minX + box.width();
        float maxY = minY + box.height();
        float maxZ = minZ + box.depth();
        float padX = box.sizeAddX != 0 ? box.sizeAddX : box.sizeAdd;
        float padY = box.sizeAddY != 0 ? box.sizeAddY : box.sizeAdd;
        float padZ = box.sizeAddZ != 0 ? box.sizeAddZ : box.sizeAdd;
        float minXp = minX - padX;
        float minYp = minY - padY;
        float minZp = minZ - padZ;
        float maxXp = maxX + padX;
        float maxYp = maxY + padY;
        float maxZp = maxZ + padZ;
        List<CubeFace> faces = new ArrayList<>();
        for (FaceDir dir : FaceDir.values()) {
            float[] uv = box.uv(dir);
            if (uv == null) continue;
            // EMF explicit per-face UV names (uvWest/uvEast) address the opposite geometric side
            // of auto-UV layout; auto UV does the reverse swap. Mirror 1.21.1 parity rules.
            boolean explicit = box.textureOffset.length != 2;
            FaceDir oppositeX = dir == FaceDir.WEST ? FaceDir.EAST
                    : dir == FaceDir.EAST ? FaceDir.WEST : dir;
            FaceDir normal = explicit ? oppositeX : dir;
            FaceDir geometry = explicit ? dir : oppositeX;
            faces.add(new CubeFace(normal, 0, vertices(geometry, minXp, minYp, minZp, maxXp, maxYp, maxZp,
                    uv[0] / data.textureWidth, uv[1] / data.textureHeight,
                    uv[2] / data.textureWidth, uv[3] / data.textureHeight)));
        }
        return new NormalizedCube(minXp, minYp, minZp, maxXp, maxYp, maxZp, faces);
    }

    private static FaceVertex[] vertices(FaceDir dir, float minX, float minY, float minZ,
                                         float maxX, float maxY, float maxZ,
                                         float u1, float v1, float u2, float v2) {
        float[][] positions = switch (dir) {
            case NORTH -> new float[][]{{maxX, minY, minZ}, {minX, minY, minZ}, {minX, maxY, minZ}, {maxX, maxY, minZ}};
            case SOUTH -> new float[][]{{minX, minY, maxZ}, {maxX, minY, maxZ}, {maxX, maxY, maxZ}, {minX, maxY, maxZ}};
            case WEST -> new float[][]{{maxX, minY, maxZ}, {maxX, minY, minZ}, {maxX, maxY, minZ}, {maxX, maxY, maxZ}};
            case EAST -> new float[][]{{minX, minY, minZ}, {minX, minY, maxZ}, {minX, maxY, maxZ}, {minX, maxY, minZ}};
            case UP -> new float[][]{{minX, minY, minZ}, {maxX, minY, minZ}, {maxX, minY, maxZ}, {minX, minY, maxZ}};
            case DOWN -> new float[][]{{maxX, maxY, maxZ}, {minX, maxY, maxZ}, {minX, maxY, minZ}, {maxX, maxY, minZ}};
        };
        // Vanilla ModelPart.Quad assigns the rectangle's (u2,v1) to its first vertex.
        float[][] uvs = {{u2, v1}, {u1, v1}, {u1, v2}, {u2, v2}};
        FaceVertex[] result = new FaceVertex[4];
        for (int i = 0; i < 4; i++) {
            result[i] = new FaceVertex(positions[i][0], positions[i][1], positions[i][2],
                    uvs[i][0], uvs[i][1]);
        }
        return result;
    }
}
