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
 * JPM face tree built directly from the resource-pack model. No eye-specific offsets, variant
 * filtering or hidden-base-face fitting is applied: the declared JPM transforms and UVs are
 * preserved, and the animation expressions are evaluated as absolute writes.
 */
public final class JpmFaceModel {
    private static final java.util.regex.Pattern EYE_VARIANT = java.util.regex.Pattern
            .compile("[rl]_(eye_white|pupil|eyelid|blink)(\\d+)$");

    private final JpmModel data;
    private final ModelPart root;
    private final float headWidth;
    private final float headHeight;
    private final float uvOffsetU;
    private final float uvOffsetV;
    private final Map<String, ModelPart> parts = new HashMap<>();
    private final Map<String, List<ModelPart.Cuboid>> baseCuboids = new HashMap<>();

    public JpmFaceModel(JpmModel data) {
        this(data, 8.0F, 8.0F, 0, 0);
    }

    public JpmFaceModel(JpmModel data, float headWidth, float headHeight) {
        this(data, headWidth, headHeight, 0, 0);
    }

    public JpmFaceModel(JpmModel data, float headWidth, float headHeight,
                        float uvOffsetU, float uvOffsetV) {
        this.data = data;
        this.headWidth = headWidth;
        this.headHeight = headHeight;
        this.uvOffsetU = uvOffsetU;
        this.uvOffsetV = uvOffsetV;
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
            target.setPivot((float) state.tx, (float) state.ty, (float) state.tz);
            target.pitch = (float) state.rx;
            target.yaw = (float) state.ry;
            target.roll = (float) state.rz;
            target.xScale = (float) state.sx;
            target.yScale = (float) state.sy;
            target.zScale = (float) state.sz;
            target.visible = state.visible && visibleVariant(part.id, context.distance);
            target.cuboids = state.visibleBoxes
                    ? baseCuboids.getOrDefault(part.id, target.cuboids) : List.of();
        }
    }

    /**
     * Selects the eye LOD group from the imported head dimensions. JustExpressions ships separate
     * 2by1/2by2/2by3/simple/farset variants; EMF shows one group for a given face size.
     */
    private boolean visibleVariant(String id, double distance) {
        java.util.regex.Matcher matcher = EYE_VARIANT.matcher(id);
        if (!matcher.matches()) return true;
        int variant = Integer.parseInt(matcher.group(2));
        if (distance > 24) return variant == 16;
        // Head bounds include inflated/custom geometry, so they are not a reliable LOD selector.
        // The standard 2by1 group is the safe default for player faces.
        return variant >= 1 && variant <= 4;
    }

    private ModelPart build(JpmPart part) {
        List<NormalizedCube> cubes = new ArrayList<>();
        for (JpmBox box : part.boxes) {
            cubes.add(toCube(box, part.id));
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
        modelPart.visible = visibleVariant(part.id, 0);
        baseCuboids.put(part.id, cuboids);
        parts.put(part.id, modelPart);
        return modelPart;
    }

    private NormalizedCube toCube(JpmBox box, String partId) {
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
            float uo = partId.matches("[rl]_(eye_white|pupil|eyelid|blink)\\d+") ? uvOffsetU : 0;
            float vo = partId.matches("[rl]_(eye_white|pupil|eyelid|blink)\\d+") ? uvOffsetV : 0;
            faces.add(new CubeFace(dir, 0, vertices(dir, minXp, minYp, minZp, maxXp, maxYp, maxZp,
                    (uv[0] + uo) / data.textureWidth, (uv[1] + vo) / data.textureHeight,
                    (uv[2] + uo) / data.textureWidth, (uv[3] + vo) / data.textureHeight)));
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
