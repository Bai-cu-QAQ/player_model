package dev.playermodel;

import dev.playermodel.jpm.JpmEvalContext;
import dev.playermodel.jpm.JpmModel;
import dev.playermodel.jpm.JpmParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JpmInlineTest {
    @Test
    void appliesInvertAndOnlyMirrorsAutoUv() throws Exception {
        String json = """
                {"id":"x","textureSize":[64,64],"submodels":[{
                  "id":"p","translate":[1,2,3],"rotate":[90,0,0],"invertAxis":"xy",
                  "mirrorTexture":"u","boxes":[
                    {"coordinates":[1,2,3,4,5,6],"uvNorth":[8,8,16,16]},
                    {"coordinates":[0,0,0,4,4,4],"textureOffset":[0,0]}
                  ]
                }]}
                """;
        var model = JpmParser.parseText(json);
        var part = model.roots.get(0);
        assertEquals(-1, part.translate[0], 1e-5);
        assertEquals(-2, part.translate[1], 1e-5);
        assertEquals(3, part.translate[2], 1e-5);
        assertEquals(-Math.PI / 2, part.rotate[0], 1e-5);
        assertArrayEquals(new float[]{8, 8, 16, 16}, part.boxes.get(0).uv(dev.playermodel.model.FaceDir.NORTH));
        float[] auto = part.boxes.get(1).uv(dev.playermodel.model.FaceDir.NORTH);
        assertTrue(auto[0] > auto[2], "mirrorTexture must swap auto-UV horizontal endpoints");
    }

    @Test
    void parsesInlineNestedModelAndAnimation() throws Exception {
        String json = """
                {
                  "id": "inline",
                  "textureSize": [64, 64],
                  "animations": [
                    {"var.t": "1 + 2", "pupil.tx": "var.t"},
                    {"lid.sy": "clamp(1 - var.t / 10, 0, 1)"}
                  ],
                  "submodels": [{
                    "id": "eyes",
                    "translate": [0, 4, -4],
                    "boxes": [{
                      "coordinates": [-1, -1, -1, 2, 2, 0.1],
                      "uvNorth": [8, 8, 16, 16],
                      "uvEast": [8, 8, 16, 16]
                    }],
                    "submodels": [{
                      "id": "pupil",
                      "translate": [0, 0, -0.2],
                      "boxes": [{"coordinates": [-0.2, -0.2, -0.1, 0.4, 0.4, 0.1],
                                 "uvNorth": [4, 4, 8, 8]}]
                    }]
                  }]
                }
                """;
        JpmModel model = JpmParser.parseText(json);
        assertEquals("inline", model.id);
        assertEquals(2, model.boxCount());
        assertEquals(2, model.animations.size());
        JpmEvalContext context = new JpmEvalContext();
        model.animate(context);
        assertEquals(3.0, context.variables.get("var.t"), 1e-9);
        assertEquals(3.0, context.parts.get("pupil").tx, 1e-9);
    }
}
