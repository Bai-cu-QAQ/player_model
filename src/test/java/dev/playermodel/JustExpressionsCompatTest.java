package dev.playermodel;

import dev.playermodel.jpm.JemParser;
import dev.playermodel.jpm.JpmModel;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JustExpressionsCompatTest {
    @Test
    void parsesInlineHeadAndHeadwearFromRealJem() throws Exception {
        Path jem = Path.of(".tools/JustExpressions/assets/minecraft/emf/cem/player.jem");
        if (!Files.isRegularFile(jem)) return;
        var parsed = JemParser.parse(jem);
        assertNotNull(parsed.face());
        JpmModel head = parsed.inlineForVanillaPart("head");
        JpmModel headwear = parsed.inlineForVanillaPart("headwear");
        assertNotNull(head);
        assertNotNull(headwear);
        assertTrue(head.boxCount() > 0);
        assertTrue(headwear.boxCount() > 0);
    }

    @Test
    void parsesInlineJemAnimations() throws Exception {
        String json = """
                {
                  "textureSize": [64, 64],
                  "models": [
                    {
                      "part": "head",
                      "id": "head",
                      "animations": [
                        {"head.ty": "2"}
                      ],
                      "boxes": [
                        {"coordinates": [-4, 24, -4, 8, 8, 8], "textureOffset": [0, 0]}
                      ]
                    }
                  ]
                }
                """;
        var parsed = JemParser.parse(json, Path.of(".tmp"));
        JpmModel head = parsed.inlineForVanillaPart("head");
        assertNotNull(head);
        var face = new dev.playermodel.runtime.JpmFaceModel(head);
        face.animate(new dev.playermodel.jpm.JpmEvalContext());
        var part = face.root().children.get("head");
        assertNotNull(part);
        assertTrue(Math.abs(part.pivotY - 2.0F) < 1e-5F);
    }

    @Test
    void parsesInlineJemFromString() throws Exception {
        String json = """
                {
                  "textureSize": [64, 64],
                  "models": [
                    {
                      "part": "head",
                      "id": "head",
                      "invertAxis": "xy",
                      "translate": [0, -24, 0],
                      "boxes": [
                        {"coordinates": [-4, 24, -4, 8, 8, 8], "textureOffset": [0, 0]}
                      ]
                    }
                  ]
                }
                """;
        var parsed = JemParser.parse(json, Path.of(".tmp"));
        JpmModel head = parsed.inlineForVanillaPart("head");
        assertNotNull(head);
        assertTrue(head.boxCount() == 1);
    }
}
