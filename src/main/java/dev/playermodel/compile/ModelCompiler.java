package dev.playermodel.compile;

import dev.playermodel.model.BoneKind;
import dev.playermodel.model.CubeFace;
import dev.playermodel.model.FaceDir;
import dev.playermodel.model.FaceVertex;
import dev.playermodel.model.NormalizedBone;
import dev.playermodel.model.NormalizedCube;
import dev.playermodel.model.NormalizedModel;
import dev.playermodel.model.NormalizedPhysicsBone;
import dev.playermodel.model.RawBone;
import dev.playermodel.model.RawElement;
import dev.playermodel.model.RawFace;
import dev.playermodel.model.RawModel;
import dev.playermodel.model.RawPhysicsBone;
import dev.playermodel.model.RawTexture;
import net.minecraft.client.model.ModelPart;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Turns the parsed raw model into final per-face geometry. Vanilla's own Cuboid is used for the
 * box-UV layout so the UV orientation matches Minecraft exactly; per-face UVs are then remapped on
 * top of the same vertex positions.
 */
public final class ModelCompiler {
    private static final EnumSet<Direction> ALL_FACES = EnumSet.allOf(Direction.class);

    private ModelCompiler() {
    }

    public static NormalizedModel compile(RawModel raw, String textureMode, String pivotMode) throws CompileException {
        TextureAtlas atlas = TextureAtlas.prepare(raw);
        NormalizedModel model = new NormalizedModel();
        model.displayName = raw.displayName;
        model.textureMode = textureMode;
        model.pivotMode = pivotMode;
        model.slim = raw.slim;
        model.textureWidth = atlas.textureWidth();
        model.textureHeight = atlas.textureHeight();
        model.texturePng = atlas.png();
        model.warnings = List.copyOf(raw.warnings);
        model.anchors = List.copyOf(raw.anchors);

        List<NormalizedBone> bones = new ArrayList<>();
        for (RawBone rawBone : raw.bones) {
            if (rawBone.elements.isEmpty()) {
                continue;
            }
            List<NormalizedCube> cubes = new ArrayList<>();
            for (RawElement element : rawBone.elements) {
                NormalizedCube cube = compileCube(raw, element, atlas);
                if (cube != null) {
                    cubes.add(cube);
                }
            }
            if (!cubes.isEmpty()) {
                bones.add(new NormalizedBone(rawBone.kind, rawBone.pivot[0], rawBone.pivot[1], rawBone.pivot[2], cubes));
            }
        }
        if (bones.isEmpty()) {
            throw new CompileException("模型没有生成任何方块");
        }
        model.bones = List.copyOf(bones);

        List<NormalizedPhysicsBone> physicsBones = new ArrayList<>();
        java.util.Set<String> physicsNames = new java.util.HashSet<>();
        for (RawPhysicsBone rawPhysics : raw.physicsBones) {
            String key = rawPhysics.name.toLowerCase(java.util.Locale.ROOT);
            if (!physicsNames.add(key)) {
                throw new CompileException("物理骨骼重名: " + rawPhysics.name);
            }
            if (rawPhysics.parentPhysics != null && !physicsNames.contains(
                    rawPhysics.parentPhysics.toLowerCase(java.util.Locale.ROOT))) {
                throw new CompileException("物理骨骼父节点不存在或顺序错误: " + rawPhysics.name);
            }
            List<NormalizedCube> physicsCubes = new ArrayList<>();
            for (RawElement element : rawPhysics.elements) {
                NormalizedCube cube = compileCube(raw, element, atlas);
                if (cube != null) {
                    physicsCubes.add(cube);
                }
            }
            physicsBones.add(new NormalizedPhysicsBone(rawPhysics.name, rawPhysics.parentBone,
                    rawPhysics.parentPhysics, rawPhysics.pivot[0], rawPhysics.pivot[1],
                    rawPhysics.pivot[2], physicsCubes));
        }
        model.physicsBones = List.copyOf(physicsBones);
        return model;
    }

    private static NormalizedCube compileCube(RawModel raw, RawElement element, TextureAtlas atlas) throws CompileException {
        float minX = Math.min(element.from[0], element.to[0]) - element.inflate;
        float minY = Math.min(element.from[1], element.to[1]) - element.inflate;
        float minZ = Math.min(element.from[2], element.to[2]) - element.inflate;
        float maxX = Math.max(element.from[0], element.to[0]) + element.inflate;
        float maxY = Math.max(element.from[1], element.to[1]) + element.inflate;
        float maxZ = Math.max(element.from[2], element.to[2]) + element.inflate;
        float sizeX = maxX - minX;
        float sizeY = maxY - minY;
        float sizeZ = maxZ - minZ;
        if (sizeX < 1e-4F) {
            maxX = minX + 1e-4F;
            sizeX = 1e-4F;
        }
        if (sizeY < 1e-4F) {
            maxY = minY + 1e-4F;
            sizeY = 1e-4F;
        }
        if (sizeZ < 1e-4F) {
            maxZ = minZ + 1e-4F;
            sizeZ = 1e-4F;
        }
        int u = Math.round(element.uvOffset[0]);
        int v = Math.round(element.uvOffset[1]);
        boolean boxUv = element.boxUv || element.faces.isEmpty();
        ModelPart.Cuboid cuboid = new ModelPart.Cuboid(u, v, minX, minY, minZ, sizeX, sizeY, sizeZ,
                0, 0, 0, boxUv && element.mirrorUv, raw.textureWidth, raw.textureHeight, ALL_FACES);

        float centerX = minX + sizeX / 2.0F;
        float centerY = minY + sizeY / 2.0F;
        float centerZ = minZ + sizeZ / 2.0F;
        List<CubeFace> faces = new ArrayList<>();
        for (ModelPart.Quad quad : cuboid.sides) {
            FaceDir dir = directionOf(quad, centerX, centerY, centerZ);
            if (dir == null) {
                continue;
            }
            RawFace rawFace = element.faces.get(dir);
            if (!boxUv && rawFace == null) {
                continue; // this side is not present in the Blockbench model
            }
            int textureIndex = rawFace != null ? rawFace.textureIndex() : raw.selectedTextureIndex;
            RawTexture faceTexture = raw.textureAt(textureIndex);
            float textureUvWidth = faceTexture != null && faceTexture.uvWidth() > 0
                    ? faceTexture.uvWidth() : raw.textureWidth;
            float textureUvHeight = faceTexture != null && faceTexture.uvHeight() > 0
                    ? faceTexture.uvHeight() : raw.textureHeight;
            FaceVertex[] vertices = new FaceVertex[4];
            float uMin = Float.POSITIVE_INFINITY;
            float uMax = Float.NEGATIVE_INFINITY;
            float vMin = Float.POSITIVE_INFINITY;
            float vMax = Float.NEGATIVE_INFINITY;
            for (ModelPart.Vertex vertex : quad.vertices) {
                uMin = Math.min(uMin, vertex.u);
                uMax = Math.max(uMax, vertex.u);
                vMin = Math.min(vMin, vertex.v);
                vMax = Math.max(vMax, vertex.v);
            }
            float rotatedMinX = Float.POSITIVE_INFINITY;
            float rotatedMinY = Float.POSITIVE_INFINITY;
            float rotatedMinZ = Float.POSITIVE_INFINITY;
            float rotatedMaxX = Float.NEGATIVE_INFINITY;
            float rotatedMaxY = Float.NEGATIVE_INFINITY;
            float rotatedMaxZ = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < 4; i++) {
                ModelPart.Vertex vertex = quad.vertices[i];
                float uu = vertex.u;
                float vv = vertex.v;
                if (!boxUv && rawFace != null) {
                    float fu = uMax - uMin < 1e-6F ? 0.5F : (vertex.u - uMin) / (uMax - uMin);
                    float fv = vMax - vMin < 1e-6F ? 0.5F : (vertex.v - vMin) / (vMax - vMin);
                    if (element.mirrorUv) {
                        fu = 1.0F - fu;
                    }
                    float[] rotated = rotateUv(fu, fv, rawFace.rotation());
                    fu = rotated[0];
                    fv = rotated[1];
                    uu = (rawFace.u1() + fu * (rawFace.u2() - rawFace.u1())) / textureUvWidth;
                    vv = (rawFace.v1() + fv * (rawFace.v2() - rawFace.v1())) / textureUvHeight;
                }
                if (atlas.hasAtlas()) {
                    uu = atlas.mapU(textureIndex, uu);
                    vv = atlas.mapV(textureIndex, vv);
                }
                float[] rotated = rotatePoint(vertex.pos.x(), vertex.pos.y(), vertex.pos.z(),
                        element.origin, element.rotation);
                float px = rotated[0];
                float py = rotated[1];
                float pz = rotated[2];
                if (!Float.isFinite(px) || !Float.isFinite(py) || !Float.isFinite(pz)
                        || !Float.isFinite(uu) || !Float.isFinite(vv)) {
                    throw new CompileException("模型包含 NaN/无限值");
                }
                rotatedMinX = Math.min(rotatedMinX, px);
                rotatedMinY = Math.min(rotatedMinY, py);
                rotatedMinZ = Math.min(rotatedMinZ, pz);
                rotatedMaxX = Math.max(rotatedMaxX, px);
                rotatedMaxY = Math.max(rotatedMaxY, py);
                rotatedMaxZ = Math.max(rotatedMaxZ, pz);
                vertices[i] = new FaceVertex(px, py, pz, uu, vv);
            }
            faces.add(new CubeFace(dir, atlas.hasAtlas() ? 0 : textureIndex, vertices));
            minX = Math.min(minX, rotatedMinX);
            minY = Math.min(minY, rotatedMinY);
            minZ = Math.min(minZ, rotatedMinZ);
            maxX = Math.max(maxX, rotatedMaxX);
            maxY = Math.max(maxY, rotatedMaxY);
            maxZ = Math.max(maxZ, rotatedMaxZ);
        }
        if (faces.isEmpty()) {
            return null;
        }
        return new NormalizedCube(minX, minY, minZ, maxX, maxY, maxZ, faces);
    }

    private static float[] rotatePoint(float x, float y, float z, float[] origin, float[] rotation) {
        double rx = Math.toRadians(rotation[0]);
        double ry = Math.toRadians(rotation[1]);
        double rz = Math.toRadians(rotation[2]);
        double px = x - origin[0];
        double py = y - origin[1];
        double pz = z - origin[2];

        // X, then Y, then Z (Blockbench's X/Y/Z rotation fields).
        double y1 = py * Math.cos(rx) - pz * Math.sin(rx);
        double z1 = py * Math.sin(rx) + pz * Math.cos(rx);
        py = y1;
        pz = z1;
        double x2 = px * Math.cos(ry) + pz * Math.sin(ry);
        double z2 = -px * Math.sin(ry) + pz * Math.cos(ry);
        px = x2;
        pz = z2;
        double x3 = px * Math.cos(rz) - py * Math.sin(rz);
        double y3 = px * Math.sin(rz) + py * Math.cos(rz);
        px = x3;
        py = y3;

        return new float[]{(float) (px + origin[0]), (float) (py + origin[1]), (float) (pz + origin[2])};
    }

    private static float[] rotateUv(float u, float v, int degrees) {
        return switch (Math.floorMod(degrees, 360)) {
            case 90 -> new float[]{1.0F - v, u};
            case 180 -> new float[]{1.0F - u, 1.0F - v};
            case 270 -> new float[]{v, 1.0F - u};
            default -> new float[]{u, v};
        };
    }

    private static FaceDir directionOf(ModelPart.Quad quad, float centerX, float centerY, float centerZ) {
        float x = 0;
        float y = 0;
        float z = 0;
        for (ModelPart.Vertex vertex : quad.vertices) {
            x += vertex.pos.x();
            y += vertex.pos.y();
            z += vertex.pos.z();
        }
        x = x / 4.0F - centerX;
        y = y / 4.0F - centerY;
        z = z / 4.0F - centerZ;
        float ax = Math.abs(x);
        float ay = Math.abs(y);
        float az = Math.abs(z);
        if (ax < 1e-5F && ay < 1e-5F && az < 1e-5F) {
            return null;
        }
        if (ax >= ay && ax >= az) {
            return x > 0 ? FaceDir.EAST : FaceDir.WEST;
        }
        if (ay >= ax && ay >= az) {
            return y > 0 ? FaceDir.UP : FaceDir.DOWN;
        }
        return z > 0 ? FaceDir.SOUTH : FaceDir.NORTH;
    }

    public static Direction direction(FaceDir dir) {
        return switch (dir) {
            case DOWN -> Direction.DOWN;
            case UP -> Direction.UP;
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case WEST -> Direction.WEST;
            case EAST -> Direction.EAST;
        };
    }

    public static final class CompileException extends Exception {
        public CompileException(String message) {
            super(message);
        }
    }

    public static Map<BoneKind, RawBone> rawBones(RawModel model) {
        Map<BoneKind, RawBone> map = new EnumMap<>(BoneKind.class);
        for (RawBone bone : model.bones) {
            map.put(bone.kind, bone);
        }
        return map;
    }
}
