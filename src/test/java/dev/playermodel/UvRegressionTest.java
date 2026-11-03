package dev.playermodel;

import dev.playermodel.compile.ModelCompiler;
import dev.playermodel.importing.BbmodelParser;
import dev.playermodel.model.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.EnumMap;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class UvRegressionTest {
    @Test
    void baiCuUsesClassicSkinBaseAndExistingOuterLayerRegions() throws Exception {
        var raw = BbmodelParser.parse(Path.of("src/test/resources/bai_cu_skin.bbmodel"), "bai_cu");
        var template = BbmodelParser.parse(Path.of("src/test/resources/player_skin_template.bbmodel"), "template");
        for (var bone : raw.bones) {
            var reference = template.bone(bone.kind).elements.get(0);
            for (int i=0; i<bone.elements.size(); i++) {
                var element = bone.elements.get(i);
                float du=0, dv=0;
                if (i>0) {
                    if (bone.kind==BoneKind.LEFT_ARM) du=16;
                    else if (bone.kind==BoneKind.LEFT_LEG) du=-16;
                    else dv=16;
                }
                for (var dir : FaceDir.values()) {
                    var expected=reference.faces.get(dir); var actual=element.faces.get(dir);
                    assertEquals(expected.u1()+du, actual.u1(), 1e-5);
                    assertEquals(expected.u2()+du, actual.u2(), 1e-5);
                    assertEquals(expected.v1()+dv, actual.v1(), 1e-5);
                    assertEquals(expected.v2()+dv, actual.v2(), 1e-5);
                }
            }
        }
        var model=ModelCompiler.compile(raw,"skin","model");
        assertEquals(3,model.physicsBones.size());
        var slice=raw.physicsBones.stream().filter(b -> b.name.equals("hair_physics")).findFirst().orElseThrow().elements.get(0);
        assertEquals(43,slice.faces.get(FaceDir.NORTH).u1());
        assertEquals(40,slice.faces.get(FaceDir.NORTH).u2());
    }
    @Test
    void signedRectanglesAndQuarterTurnsMatchBlockbenchSampling() throws Exception {
        RawModel raw = BbmodelParser.parse(Path.of("src/test/resources/player_vanilla.bbmodel"), "rotation");
        RawElement e = raw.bone(BoneKind.HEAD).elements.get(0);
        e.boxUv = false;
        e.faces = new EnumMap<>(FaceDir.class);
        for (int angle : new int[]{0,90,180,270}) {
            e.faces.put(FaceDir.NORTH, new RawFace(24, 4, 8, 20, angle, 0));
            var face = ModelCompiler.compile(raw,"model","model").bone(BoneKind.HEAD).cubes().get(0).faces().get(0);
            for (var v : face.vertices()) {
                float s = (4-v.x())/8, t = (v.y()+8)/8;
                float a=s,b=t;
                if(angle==90){a=t;b=1-s;} else if(angle==180){a=1-s;b=1-t;} else if(angle==270){a=1-t;b=s;}
                assertEquals((24-16*a)/64, v.u(), 1e-6);
                assertEquals((4+16*b)/64, v.v(), 1e-6);
            }
        }
    }
    @Test
    void playerTemplateMatchesVanillaAtEverySpatialVertex() throws Exception {
        var raw = BbmodelParser.parse(Path.of("src/test/resources/player_skin_template.bbmodel"), "template");
        var template = ModelCompiler.compile(raw, "skin", "model");
        for (var bone : raw.bones) for (var e : bone.elements) {
            e.boxUv = true;
        }
        var original = ModelCompiler.compile(raw, "skin", "model");
        for (var bone : original.bones) {
            var a = bone.cubes().get(0);
            var b = template.bone(bone.kind()).cubes().get(0);
            for (var face : a.faces()) {
                var other = b.faces().stream().filter(f -> f.dir() == face.dir()).findFirst().orElseThrow();
                for (var v : face.vertices()) {
                    var match = java.util.Arrays.stream(other.vertices()).filter(w ->
                        Math.abs(v.x()-w.x()) < 1e-5 && Math.abs(v.y()-w.y()) < 1e-5 && Math.abs(v.z()-w.z()) < 1e-5).findFirst().orElseThrow();
                    assertEquals(v.u(), match.u(), 1e-6, bone.kind()+" "+face.dir()+" U @ "+v);
                    assertEquals(v.v(), match.v(), 1e-6, bone.kind()+" "+face.dir()+" V @ "+v);
                }
            }
        }
    }
    @Test
    void explicitFaceMirrorFlagDoesNotFlipAlreadySignedEndpoints() throws Exception {
        RawModel raw = BbmodelParser.parse(Path.of("src/test/resources/player_vanilla.bbmodel"), "mirror");
        RawElement e = raw.bone(BoneKind.HEAD).elements.get(0);
        e.boxUv = false;
        e.faces = new EnumMap<>(FaceDir.class);
        e.faces.put(FaceDir.NORTH, new RawFace(16, 8, 8, 16, 90, 0));
        var expected = ModelCompiler.compile(raw, "model", "model").bone(BoneKind.HEAD).cubes().get(0);
        e.mirrorUv = true;
        var actual = ModelCompiler.compile(raw, "model", "model").bone(BoneKind.HEAD).cubes().get(0);
        assertArrayEquals(expected.faces().get(0).vertices(), actual.faces().get(0).vertices());
    }
    @Test
    void topAndBottomSelectTheCorrectFace() throws Exception {
        RawModel raw = BbmodelParser.parse(Path.of("src/test/resources/player_vanilla.bbmodel"), "uv");
        RawElement element = raw.bone(BoneKind.HEAD).elements.get(0);
        element.boxUv = false;
        element.faces = new EnumMap<>(FaceDir.class);
        for (FaceDir dir : FaceDir.values()) {
            float start = dir.ordinal() * 8;
            element.faces.put(dir, new RawFace(start, 0, start + 4, 4, 0, 0));
        }
        var cube = ModelCompiler.compile(raw, "model", "model").bone(BoneKind.HEAD).cubes().get(0);
        for (CubeFace face : cube.faces()) {
            for (FaceVertex v : face.vertices()) {
                if (face.dir() == FaceDir.UP) assertEquals(-8, v.y(), 1e-5);
                if (face.dir() == FaceDir.DOWN) assertEquals(0, v.y(), 1e-5);
                float start = face.dir().ordinal() * 8 / 64F;
                assertTrue(v.u() >= start && v.u() <= start + 4 / 64F);
            }
        }
    }

    @Test
    void atlasMapsNormalizedUvToActualImagePixelsNotLogicalResolution() throws Exception {
        RawModel raw = BbmodelParser.parse(Path.of("src/test/resources/player_vanilla.bbmodel"), "atlas");
        raw.bones.removeIf(b -> b.kind != BoneKind.HEAD);
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB), "png", out);
        var tex = new RawTexture("hires", out.toByteArray(), 128, 128, 64, 64);
        raw.textures.clear(); raw.textures.add(tex); raw.textures.add(tex);
        raw.texture = tex;
        RawElement e = raw.bone(BoneKind.HEAD).elements.get(0);
        e.boxUv = false;
        e.faces = new EnumMap<>(FaceDir.class);
        e.faces.put(FaceDir.NORTH, new RawFace(0, 0, 64, 64, 0, 0));
        e.faces.put(FaceDir.SOUTH, new RawFace(0, 0, 64, 64, 0, 1));
        var model = ModelCompiler.compile(raw, "model", "model");
        assertEquals(130, model.textureWidth);
        for (CubeFace f : model.bone(BoneKind.HEAD).cubes().get(0).faces()) {
            float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
            for (FaceVertex v : f.vertices()) { min = Math.min(min, v.u()); max = Math.max(max, v.u()); }
            assertEquals(1F / 130, min, 1e-6);
            assertEquals(129F / 130, max, 1e-6);
        }
    }
}
