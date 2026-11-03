package dev.playermodel;

import dev.playermodel.jpm.JpmEvalContext;
import dev.playermodel.jpm.JpmModel;
import dev.playermodel.jpm.JpmParser;
import dev.playermodel.runtime.JpmFaceModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JpmInlineTest {
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
        JpmFaceModel face = new JpmFaceModel(model);
        assertNotNull(face.root());
        face.apply(context);
    }
}
