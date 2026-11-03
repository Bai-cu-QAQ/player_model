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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A JPM face tree ready to attach under an imported player head. Geometry is built once; the JPM
 * expression evaluator writes part state into this tree before it is copied into a render snapshot.
 */
public final class JpmFaceModel {
    private final JpmModel data;
    private final ModelPart root;
    private final Map<String, ModelPart> parts = new HashMap<>();

    public JpmFaceModel(JpmModel data) {
        this.data = data;
        Map<String, ModelPart> children = new HashMap<>();
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
            // JPM properties are absolute pivot/rotation/scale writes, not offsets.
            target.setPivot((float) state.tx, (float) state.ty + layerYOffset(part.id),
                    (float) state.tz);
            target.pitch = (float) state.rx;
            target.yaw = (float) state.ry;
            target.roll = (float) state.rz;
            target.xScale = (float) state.sx;
            target.yScale = (float) state.sy;
            target.zScale = (float) state.sz;
            target.visible = state.visible;
        }
    }

    private ModelPart build(JpmPart part) {
        List<NormalizedCube> cubes = new ArrayList<>();
        for (JpmBox box : part.boxes) {
            cubes.add(toCube(box));
        }
        List<ModelPart.Cuboid> cuboids = PreparedModel.buildCuboids(cubes,
                part.translate[0], part.translate[1], part.translate[2]);
        Map<String, ModelPart> children = new HashMap<>();
        for (JpmPart child : part.children) {
            children.put(child.id, build(child));
        }
        ModelPart modelPart = new ModelPart(cuboids, children);
        modelPart.setPivot(part.translate[0], part.translate[1] + layerYOffset(part.id),
                part.translate[2]);
        modelPart.pitch = part.rotate[0];
        modelPart.yaw = part.rotate[1];
        modelPart.roll = part.rotate[2];
        modelPart.xScale = part.scale;
        modelPart.yScale = part.scale;
        modelPart.zScale = part.scale;
        parts.put(part.id, modelPart);
        return modelPart;
    }

    /**
     * JustExpressions' white layer is authored one pixel above the pupil/lid band. The hidden
     * base face uses the normal eye line, so bring the white down to the same band.
     */
    private static float layerYOffset(String id) {
        return id.matches("[rl]_eye_white\\d+") ? 1.0F : 0;
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
            faces.add(new CubeFace(dir, 0, vertices(dir, minXp, minYp, minZp, maxXp, maxYp, maxZp,
                    uv[0] / data.textureWidth, uv[1] / data.textureHeight,
                    uv[2] / data.textureWidth, uv[3] / data.textureHeight)));
        }
        return new NormalizedCube(minXp, minYp, minZp, maxXp, maxYp, maxZp, faces);
    }

    private static FaceVertex[] vertices(FaceDir dir, float minX, float minY, float minZ,
                                         float maxX, float maxY, float maxZ,
                                         float u1, float v1, float u2, float v2) {
        float[][] positions = switch (dir) {
            case NORTH -> new float[][]{{minX, minY, minZ}, {maxX, minY, minZ}, {maxX, maxY, minZ}, {minX, maxY, minZ}};
            case SOUTH -> new float[][]{{maxX, minY, maxZ}, {minX, minY, maxZ}, {minX, maxY, maxZ}, {maxX, maxY, maxZ}};
            case EAST -> new float[][]{{maxX, minY, maxZ}, {maxX, minY, minZ}, {maxX, maxY, minZ}, {maxX, maxY, maxZ}};
            case WEST -> new float[][]{{minX, minY, minZ}, {minX, minY, maxZ}, {minX, maxY, maxZ}, {minX, maxY, minZ}};
            case UP -> new float[][]{{minX, minY, minZ}, {maxX, minY, minZ}, {maxX, minY, maxZ}, {minX, minY, maxZ}};
            case DOWN -> new float[][]{{minX, maxY, maxZ}, {maxX, maxY, maxZ}, {maxX, maxY, minZ}, {minX, maxY, minZ}};
        };
        float[][] uvs = {{u1, v1}, {u2, v1}, {u2, v2}, {u1, v2}};
        FaceVertex[] result = new FaceVertex[4];
        for (int i = 0; i < 4; i++) {
            result[i] = new FaceVertex(positions[i][0], positions[i][1], positions[i][2],
                    uvs[i][0], uvs[i][1]);
        }
        return result;
    }
}
