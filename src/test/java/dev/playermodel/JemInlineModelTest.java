package dev.playermodel;

import dev.playermodel.jpm.JemModel;
import dev.playermodel.jpm.JemParser;
import dev.playermodel.jpm.JemPart;
import dev.playermodel.jpm.JpmBox;
import dev.playermodel.jpm.JpmException;
import dev.playermodel.jpm.JpmModel;
import dev.playermodel.jpm.JpmPart;
import dev.playermodel.model.FaceDir;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JemInlineModelTest {
    private static final Path JEM = Path.of(".tools/JustExpressions/assets/minecraft/emf/cem/player.jem");

    @Test
    void parsesInlineBoxesSubmodelsAndOverrides(@TempDir Path dir) throws Exception {
        Path jem = dir.resolve("inline.jem");
        Files.writeString(jem, """
                {
                  "textureSize": [32, 48],
                  "models": [
                    {
                      "part": "head",
                      "id": "inline_head",
                      "attach": true,
                      "texture": "face.png",
                      "textureSize": [16, 24],
                      "invertAxis": "xy",
                      "translate": [1, -2, 3],
                      "rotate": [0, 0, 10],
                      "boxes": [
                        {"coordinates": [-4, 24, -4, 8, 8, 8], "textureOffset": [0, 0], "sizeAdd": 1},
                        {"coordinates": [0, 0, 0, 1, 1, 1], "uvNorth": [1, 2, 3, 4], "uvUp": [5, 6, 7, 8]}
                      ],
                      "submodels": [
                        {"id": "child", "translate": [0, 1, 0],
                         "boxes": [{"coordinates": [0, 0, 0, 2, 2, 2], "textureOffset": [4, 4]}]}
                      ]
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);

        JemModel model = JemParser.parse(jem);
        assertEquals(32, model.textureWidth());
        assertEquals(48, model.textureHeight());
        assertEquals(1, model.models().size());

        JemPart part = model.forVanillaPart("head");
        assertNotNull(part);
        assertEquals("inline_head", part.id());
        assertTrue(part.attach());
        assertEquals("face.png", part.texture());
        assertEquals(16, part.textureWidth());
        assertEquals(24, part.textureHeight());
        assertNull(part.jpm());
        assertNotNull(part.inline());
        assertTrue(part.hasInlineModel());

        JpmModel inline = model.inlineForVanillaPart("head");
        assertNotNull(inline);
        assertEquals("inline_head", inline.id);
        assertEquals(16, inline.textureWidth);
        assertEquals(24, inline.textureHeight);
        assertEquals("face.png", inline.texture);
        assertEquals(1, inline.roots.size());

        JpmPart root = inline.roots.get(0);
        assertEquals("inline_head", root.id);
        assertEquals(-1.0F, root.translate[0], 1e-5F);
        assertEquals(2.0F, root.translate[1], 1e-5F);
        assertEquals(3.0F, root.translate[2], 1e-5F);
        assertEquals((float) Math.toRadians(10), root.rotate[2], 1e-5F);
        assertEquals(2, root.boxes.size());
        assertEquals(1, root.children.size());
        assertEquals("child", root.children.get(0).id);
        assertEquals(1, root.children.get(0).boxes.size());

        JpmBox first = root.boxes.get(0);
        assertEquals(-4.0F, first.x(), 1e-5F);
        assertEquals(-32.0F, first.y(), 1e-5F);
        assertEquals(-4.0F, first.z(), 1e-5F);
        assertEquals(1.0F, first.sizeAdd, 1e-5F);

        JpmBox second = root.boxes.get(1);
        assertNotNull(second.uv(FaceDir.NORTH));
        assertEquals(1.0F, second.uv(FaceDir.NORTH)[0], 1e-5F);
        assertEquals(4.0F, second.uv(FaceDir.NORTH)[3], 1e-5F);
        assertNotNull(second.uv(FaceDir.UP));
    }

    @Test
    void preservesReferencedJpmAndAttachmentIteration(@TempDir Path dir) throws Exception {
        Path jpm = dir.resolve("face.jpm");
        Files.writeString(jpm, """
                {"id":"face","textureSize":[64,64],"submodels":[
                  {"id":"eye","boxes":[{"coordinates":[-1,0,0,2,1,1],"textureOffset":[0,0]}]}
                ]}
                """, StandardCharsets.UTF_8);
        Path jem = dir.resolve("refs.jem");
        Files.writeString(jem, """
                {"textureSize":[64,64],"models":[
                  {"part":"head","id":"face","model":"face.jpm"},
                  {"part":"head","id":"head","boxes":[
                    {"coordinates":[-4,24,-4,8,8,8],"textureOffset":[0,0]}
                  ]},
                  {"part":"body","id":"body","attach":false}
                ]}
                """, StandardCharsets.UTF_8);

        JemModel model = JemParser.parse(jem);
        assertEquals(2, model.forVanillaParts("head").size());
        assertEquals(3, model.attachments().size());
        assertNotNull(model.face());
        assertEquals("face", model.face().id);

        JemPart face = model.forVanillaPart("head");
        assertNotNull(face);
        assertEquals("face", face.id());
        assertNotNull(face.jpm());
        assertNull(face.inline());
        assertFalse(face.attach());

        JemPart inlineHead = model.forVanillaParts("head").get(1);
        assertNull(inlineHead.jpm());
        assertNotNull(inlineHead.inline());
        assertNotNull(model.inlineForVanillaPart("head"));
    }

    @Test
    void rejectsReferencedModelOutsideDirectory(@TempDir Path dir) throws Exception {
        Path jem = dir.resolve("escape.jem");
        Files.writeString(jem, """
                {"models":[{"part":"head","id":"bad","model":"../outside.jpm"}]}
                """, StandardCharsets.UTF_8);
        assertThrows(JpmException.class, () -> JemParser.parse(jem));
    }

    @Test
    void parsesRealJustExpressionsJemInlineParts() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(JEM), "local JustExpressions resource pack is unavailable");
        JemModel model = JemParser.parse(JEM);
        assertEquals(13, model.attachments().size());
        assertEquals(2, model.forVanillaParts("head").size());

        JemPart face = model.forVanillaPart("head");
        assertNotNull(face);
        assertEquals("player_face", face.id());
        assertNotNull(face.jpm());
        assertNull(face.inline());

        JpmModel head = model.inlineForVanillaPart("head");
        assertNotNull(head);
        assertEquals("head", head.id);
        assertEquals(64, head.textureWidth);
        assertEquals(64, head.textureHeight);
        assertEquals(1, head.roots.size());
        JpmPart root = head.roots.get(0);
        assertEquals(1, root.boxes.size());
        assertEquals(1, root.children.size());
        // invertAxis xy on the head entry.
        assertEquals(24.0F, root.translate[1], 1e-5F);
        assertEquals(-32.0F, root.boxes.get(0).y(), 1e-5F);

        JpmModel headwear = model.inlineForVanillaPart("headwear");
        assertNotNull(headwear);
        assertEquals(0.5F, headwear.roots.get(0).boxes.get(0).sizeAdd, 1e-5F);

        JpmModel rightArm = model.inlineForVanillaPart("right_arm");
        assertNotNull(rightArm);
        assertEquals(5.0F, rightArm.roots.get(0).translate[0], 1e-5F);
        assertEquals(22.0F, rightArm.roots.get(0).translate[1], 1e-5F);
    }
}
