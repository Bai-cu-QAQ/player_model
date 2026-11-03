package dev.playermodel.runtime;

import dev.playermodel.compile.ModelCompiler;
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
import java.util.EnumMap;
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
    private final Map<String, float[]> origins = new HashMap<>();

    public JpmFaceModel(JpmModel data) {
        this.data = data;
        Map<String, ModelPart> children = new HashMap<>();
        for (JpmPart part : data.roots) {
            children.put(part.id, build(part));
        }
        this.root = new ModelPart(List.of(), children);
    }

    public JpmModel data() {
        return data;
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
            float[] base = origins.get(part.id);
            if (base == null) base = new float[3];
            if (state != null) {
                target.setPivot(base[0] + (float) state.tx, base[1] + (float) state.ty,
                        base[2] + (float) state.tz);
                target.pitch = (float) state.rx;
                target.yaw = (float) state.ry;
                target.roll = (float) state.rz;
                target.xScale = (float) state.sx;
                target.yScale = (float) state.sy;
                target.zScale = (float) state.sz;
                target.visible = state.visible;
            }
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
        modelPart.setPivot(part.translate[0], part.translate[1], part.translate[2]);
        modelPart.pitch = part.rotate[0];
        modelPart.yaw = part.rotate[1];
        modelPart.roll = part.rotate[2];
        modelPart.xScale = part.scale;
        modelPart.yScale = part.scale;
        modelPart.zScale = part.scale;
        parts.put(part.id, modelPart);
        origins.put(part.id, part.translate.clone());
        return modelPart;
    }

    private NormalizedCube toCube(JpmBox box) {
        float minX = box.x();
        float minY = box.y();
        float minZ = box.z();
        float maxX = minX + box.width();
        float maxY = minY + box.height();
        float maxZ = minZ + box.depth();
        List<CubeFace> faces = new ArrayList<>();
        for (FaceDir dir : FaceDir.values()) {
            float[] uv = box.uv(dir);
            if (uv == null) continue;
            faces.add(new CubeFace(dir, 0, vertices(dir, minX, minY, minZ, maxX, maxY, maxZ,
                    uv[0] / data.textureWidth, uv[1] / data.textureHeight,
                    uv[2] / data.textureWidth, uv[3] / data.textureHeight)));
        }
        return new NormalizedCube(minX, minY, minZ, maxX, maxY, maxZ, faces);
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
