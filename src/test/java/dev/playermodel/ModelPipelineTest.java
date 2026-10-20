package dev.playermodel;

import dev.playermodel.compile.ModelCompiler;
import dev.playermodel.importing.BbmodelParser;
import dev.playermodel.model.BoneKind;
import dev.playermodel.model.CubeFace;
import dev.playermodel.model.FaceVertex;
import dev.playermodel.model.ModelBundle;
import dev.playermodel.model.NormalizedBone;
import dev.playermodel.model.NormalizedCube;
import dev.playermodel.model.NormalizedModel;
import dev.playermodel.model.PhysicsBoneName;
import dev.playermodel.model.PhysicsConfig;
import dev.playermodel.model.RawBone;
import dev.playermodel.model.RawModel;
import dev.playermodel.runtime.PreparedModel;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

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
