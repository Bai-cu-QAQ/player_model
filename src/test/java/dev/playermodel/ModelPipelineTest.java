package dev.playermodel;

import dev.playermodel.compile.ModelCompiler;
import dev.playermodel.importing.BbmodelParser;
import dev.playermodel.model.BoneKind;
import dev.playermodel.model.CubeFace;
import dev.playermodel.model.FaceDir;
import dev.playermodel.model.FaceVertex;
import dev.playermodel.model.ModelBundle;
import dev.playermodel.model.NormalizedBone;
import dev.playermodel.model.NormalizedCube;
import dev.playermodel.model.NormalizedModel;
import dev.playermodel.model.PhysicsBoneName;
import dev.playermodel.model.PhysicsConfig;
import dev.playermodel.util.JsonComments;
import dev.playermodel.model.RawBone;
import dev.playermodel.model.RawElement;
import dev.playermodel.model.RawFace;
import dev.playermodel.model.RawModel;
import dev.playermodel.runtime.PreparedModel;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelPipelineTest {
    private static final Path VANILLA = Path.of("src/test/resources/player_vanilla.bbmodel");
    private static final Path SMALL = Path.of("src/test/resources/player_small.bbmodel");

    @Test
    void parsesVanillaSpaceModel() throws Exception {
        RawModel raw = BbmodelParser.parse(VANILLA, "test");
        assertEquals(6, raw.bones.size());
        assertEquals(64, raw.textureWidth);
        RawBone head = raw.bone(BoneKind.HEAD);
        RawBone arm = raw.bone(BoneKind.RIGHT_ARM);
        RawBone leg = raw.bone(BoneKind.RIGHT_LEG);
        assertNotNull(head);
        assertNotNull(arm);
        assertNotNull(leg);
        assertEquals(0.0F, head.pivot[1], 0.001F);
        assertEquals(2.0F, arm.pivot[1], 0.001F);
        assertEquals(12.0F, leg.pivot[1], 0.001F);
        assertEquals(1, head.elements.size());
    }

    @Test
    void detectsBlockbenchSpaceAndSmallPivots() throws Exception {
        RawModel raw = BbmodelParser.parse(SMALL, "small");
        RawBone head = raw.bone(BoneKind.HEAD);
        RawBone leg = raw.bone(BoneKind.RIGHT_LEG);
        assertNotNull(head);
        assertNotNull(leg);
        assertEquals(9.6F, head.pivot[1], 0.01F);
        assertEquals(16.8F, leg.pivot[1], 0.01F);
    }

    @Test
    void compilesBoxUvFaces() throws Exception {
        RawModel raw = BbmodelParser.parse(VANILLA, "test");
        NormalizedModel model = ModelCompiler.compile(raw, "model", "model");
        NormalizedBone head = model.bone(BoneKind.HEAD);
        assertNotNull(head);
        assertEquals(1, head.cubes().size());
        NormalizedCube cube = head.cubes().get(0);
        assertEquals(6, cube.faces().size());
        for (CubeFace face : cube.faces()) {
            assertEquals(4, face.vertices().length);
            for (FaceVertex vertex : face.vertices()) {
                assertTrue(vertex.u() >= -0.001F && vertex.u() <= 1.001F, "u=" + vertex.u());
                assertTrue(vertex.v() >= -0.001F && vertex.v() <= 1.001F, "v=" + vertex.v());
            }
        }
    }

    @Test
    void compileAndBundleRoundTrip() throws Exception {
        RawModel raw = BbmodelParser.parse(VANILLA, "test");
        NormalizedModel model = ModelCompiler.compile(raw, "model", "model");
        ModelBundle bundle = ModelBundle.create(model, raw.texture.png());
        ModelBundle decoded = ModelBundle.fromBytes(bundle.bytes);
        assertEquals(bundle.hash, decoded.hash);
        assertEquals(model.bones.size(), decoded.model.bones.size());
        PreparedModel prepared = new PreparedModel(decoded.model);
        var cuboids = prepared.cuboids(true, java.util.Map.of());
        assertEquals(1, cuboids.get(BoneKind.HEAD).size());
    }

    @Test
    void mergesMultipleTexturesAndKeepsRotatedCube() throws Exception {
        Path multi = Path.of("src/test/resources/test_multi.bbmodel");
        RawModel raw = BbmodelParser.parse(multi, "multi");
        assertEquals(2, raw.textures.size());
        NormalizedModel model = ModelCompiler.compile(raw, "model", "model");
        assertNotNull(model.texturePng);
        assertTrue(model.textureWidth > 16, "atlas should be wider than one 16px texture");
        NormalizedBone head = model.bone(BoneKind.HEAD);
        assertNotNull(head);
        NormalizedCube cube = head.cubes().get(0);
        assertTrue(cube.faces().stream().allMatch(face -> face.textureIndex() == 0));
        for (CubeFace face : cube.faces()) {
            for (FaceVertex vertex : face.vertices()) {
                assertTrue(vertex.u() >= 0.0F && vertex.u() <= 1.0F);
                assertTrue(vertex.v() >= 0.0F && vertex.v() <= 1.0F);
            }
        }
    }

    @Test
    void parsesAndCompilesPhysicsBones() throws Exception {
        Path physics = Path.of("src/test/resources/player_physics.bbmodel");
        RawModel raw = BbmodelParser.parse(physics, "physics");
        assertEquals(1, raw.physicsBones.size());
        assertEquals("hair_physics", raw.physicsBones.get(0).name);
        assertEquals(BoneKind.HEAD, raw.physicsBones.get(0).parentBone);
        assertEquals(1, raw.bone(BoneKind.HEAD).elements.size(), "physics cube must not merge into head");
        NormalizedModel model = ModelCompiler.compile(raw, "model", "model");
        assertEquals(1, model.physicsBones.size());
        assertEquals(1, model.physicsBones.get(0).cubes().size());
        ModelBundle bundle = ModelBundle.create(model, raw.texture.png());
        NormalizedModel decoded = ModelBundle.fromBytes(bundle.bytes).model;
        assertEquals(1, decoded.physicsBones.size());
        assertEquals(1, decoded.physicsBones.get(0).cubes().size());
        PreparedModel prepared = new PreparedModel(model);
        assertEquals(1, prepared.physicsBones().size());
        assertNotNull(prepared.physicsBones().get(0).cuboids());
    }

    @Test
    void classifiesTopAndBottomFaces() throws Exception {
        RawModel raw = BbmodelParser.parse(VANILLA, "uv");
        RawElement element = raw.bone(BoneKind.HEAD).elements.get(0);
        element.boxUv = false;
        element.faces = new java.util.EnumMap<>(FaceDir.class);
        for (FaceDir dir : FaceDir.values()) {
            int start = dir.ordinal() * 8;
            element.faces.put(dir, new RawFace(start, 0, start + 4, 4, 0, 0));
        }
        NormalizedModel model = ModelCompiler.compile(raw, "model", "model");
        NormalizedBone head = model.bone(BoneKind.HEAD);
        assertNotNull(head);
        boolean up = false;
        boolean down = false;
        for (NormalizedCube cube : head.cubes()) {
            for (CubeFace face : cube.faces()) {
                if (face.dir() == FaceDir.UP) {
                    up = true;
                    for (FaceVertex v : face.vertices()) assertEquals(-8.0F, v.y(), 1e-5F);
                }
                if (face.dir() == FaceDir.DOWN) {
                    down = true;
                    for (FaceVertex v : face.vertices()) assertEquals(0.0F, v.y(), 1e-5F);
                }
            }
        }
        assertTrue(up, "top face must be classified as UP");
        assertTrue(down, "bottom face must be classified as DOWN");
    }

    @Test
    void swapLimbPositionsExchangesLeftAndRight() throws Exception {
        RawModel raw = BbmodelParser.parse(VANILLA, "swap");
        RawBone leftArm = raw.bone(BoneKind.LEFT_ARM);
        RawBone rightArm = raw.bone(BoneKind.RIGHT_ARM);
        RawBone leftLeg = raw.bone(BoneKind.LEFT_LEG);
        RawBone rightLeg = raw.bone(BoneKind.RIGHT_LEG);
        assertNotNull(leftArm);
        assertNotNull(rightArm);
        assertNotNull(leftLeg);
        assertNotNull(rightLeg);
        float[] leftArmPivot = leftArm.pivot.clone();
        float[] rightArmPivot = rightArm.pivot.clone();
        float[] leftLegPivot = leftLeg.pivot.clone();
        float[] rightLegPivot = rightLeg.pivot.clone();
        raw.swapLimbPositions();
        assertArrayEquals(rightArmPivot, raw.bone(BoneKind.LEFT_ARM).pivot, 1e-6F);
        assertArrayEquals(leftArmPivot, raw.bone(BoneKind.RIGHT_ARM).pivot, 1e-6F);
        assertArrayEquals(rightLegPivot, raw.bone(BoneKind.LEFT_LEG).pivot, 1e-6F);
        assertArrayEquals(leftLegPivot, raw.bone(BoneKind.RIGHT_LEG).pivot, 1e-6F);
        NormalizedBone compiled = ModelCompiler.compile(raw, "model", "model").bone(BoneKind.LEFT_ARM);
        assertNotNull(compiled);
        assertArrayEquals(rightArmPivot, new float[]{compiled.pivotX(), compiled.pivotY(), compiled.pivotZ()},
                1e-6F);
    }

    @Test
    void demoPhysicsModelHasTwoChains() throws Exception {
        Path demo = Path.of("src/test/resources/demo_physics.bbmodel");
        RawModel raw = BbmodelParser.parse(demo, "demo_physics");
        assertEquals(4, raw.physicsBones.size());
        NormalizedModel model = ModelCompiler.compile(raw, "model", "model");
        assertEquals(4, model.physicsBones.size());
        long hair = model.physicsBones.stream().filter(b -> b.name().startsWith("hair_")).count();
        long skirt = model.physicsBones.stream().filter(b -> b.name().startsWith("skirt_")).count();
        assertEquals(2, hair);
        assertEquals(2, skirt);
    }

    @Test
    void parsesPhysicsTuningCoefficients() {
        String json = """
                {"defaultSettings":{"substeps":10,"springBase":5.5,
                "dampingRatio":0.7,"clothSpeedScale":0.6,"liftScale":1.4,"airDrag":3.0,"targetResponse":2.5},
                "bones":{"hair_physics":{"pitchOmegaScale":0.8}}}
                """;
        PhysicsConfig config = new Gson().fromJson(json, PhysicsConfig.class);
        assertEquals(10, config.resolved("hair_physics").substeps(8));
        assertEquals(5.5F, config.resolved("hair_physics").springBase(6.0F), 0.001F);
        assertEquals(0.8F, config.resolved("hair_physics").pitchOmegaScale(0.6F), 0.001F);
        assertEquals(0.6F, config.resolved("hair_physics").clothSpeedScale(0.8F), 0.001F);
        assertEquals(0.7F, config.resolved("hair_physics").dampingRatio(0.82F), 0.001F);
        assertEquals(1.4F, config.resolved("hair_physics").liftScale(1.25F), 0.001F);
        assertEquals(3.0F, config.resolved("hair_physics").airDrag(2.0F), 0.001F);
        assertEquals(2.5F, config.resolved("hair_physics").targetResponse(4.0F), 0.001F);
    }
    @Test
    void stripsCommentsFromMetaJson() {
        String input = "{\n  // line comment\n  \"url\": \"http://example.com/a//b\",\n  /* block */\n  \"value\": 1\n}";
        String stripped = JsonComments.strip(input);
        assertTrue(stripped.contains("http://example.com/a//b"));
        assertTrue(!stripped.contains("line comment"));
        assertTrue(!stripped.contains("block"));
    }
    @Test
    void supportsStaticBoneRotation() throws Exception {
        Path rotated = Path.of("src/test/resources/player_rotated.bbmodel");
        RawModel raw = BbmodelParser.parse(rotated, "rotated");
        RawBone head = raw.bone(BoneKind.HEAD);
        assertNotNull(head);
        assertTrue(Math.abs(head.rotation[1] - 15.0F) < 0.001F);
        NormalizedModel model = ModelCompiler.compile(raw, "model", "model");
        NormalizedBone normalized = model.bone(BoneKind.HEAD);
        assertNotNull(normalized);
        assertTrue(Math.abs(normalized.rotY() - 15.0F) < 0.001F);
    }
    @Test
    void strictPhysicsSuffixAndOverrides() {
        assertTrue(PhysicsBoneName.hasSuffix("hair_physics"));
        assertEquals("hair", PhysicsBoneName.baseName("Hair_Physics"));
        assertTrue(!PhysicsBoneName.hasSuffix("hairphysics"));
        assertTrue(!PhysicsBoneName.hasSuffix("hair.physics"));
        assertEquals(null, PhysicsBoneName.baseName("_physics"));
        PhysicsConfig config = new PhysicsConfig();
        config.defaultSettings.mode = "pendulum";
        config.defaultSettings.stiffness = 90.0F;
        PhysicsConfig.Settings override = new PhysicsConfig.Settings();
        override.mode = "cloth";
        override.maxAngle = 80.0F;
        config.bones.put("skirt_physics", override);
        PhysicsConfig.Settings cloth = config.resolved("skirt_physics");
        assertEquals("cloth", cloth.mode);
        assertEquals(80.0F, cloth.maxAngle(55.0F), 0.001F);
        assertEquals(90.0F, cloth.stiffness(10.0F), 0.001F);
    }

    @Test
    void rejectsMeshElements() {
        Path mesh = Path.of("src/test/resources/mesh.bbmodel");
        try {
            BbmodelParser.parse(mesh, "mesh");
            throw new AssertionError("mesh should be rejected");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("mesh"), expected.getMessage());
        }
    }
}
