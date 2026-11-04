package dev.playermodel;

import dev.playermodel.jpm.JpmEvalContext;
import dev.playermodel.jpm.JpmModel;
import dev.playermodel.jpm.JpmParser;
import dev.playermodel.model.NormalizedModel;
import dev.playermodel.runtime.JpmFaceModel;
import dev.playermodel.runtime.PreparedModel;
import net.minecraft.client.model.ModelPart;
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
                  "texture": "face/face.png",
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
        assertEquals("face/face.png", model.texture);
        assertEquals(2, model.boxCount());
        assertEquals(2, model.animations.size());
        JpmEvalContext context = new JpmEvalContext();
        model.animate(context);
        assertEquals(3.0, context.variables.get("var.t"), 1e-9);
        assertEquals(3.0, context.parts.get("pupil").tx, 1e-9);
        JpmFaceModel face = new JpmFaceModel(model);
        assertNotNull(face.root());
        face.apply(context);
        NormalizedModel normalized = new NormalizedModel();
        normalized.faceJpm = json;
        normalized.faceTexturePng = new byte[]{1, 2, 3};
        PreparedModel prepared = new PreparedModel(normalized);
        assertNotNull(prepared.face());
        assertArrayEquals(new byte[]{1, 2, 3}, prepared.faceTexturePng());
    }

    @Test
    void jpmTranslationPropertiesAreAbsolute() throws Exception {
        String json = """
                {"id":"pose","textureSize":[64,64],"animations":[
                  {"pupil.tx":"0.1","pupil.ty":"0.2"}
                ],"submodels":[
                  {"id":"eye_white","translate":[0.5,1,0],"boxes":[{"coordinates":[-1,-1,-1,2,2,1],"uvNorth":[0,0,2,2]}]},
                  {"id":"pupil","translate":[0.5,0.5,0],"boxes":[{"coordinates":[-1,-1,-1,2,2,1],"uvNorth":[0,0,2,2]}]}
                ]}
                """;
        JpmFaceModel face = new JpmFaceModel(JpmParser.parseText(json));
        JpmEvalContext context = new JpmEvalContext();
        face.animate(context);
        assertEquals(0.1F, find(face.root(), "pupil").pivotX, 1e-5F);
        assertEquals(0.2F, find(face.root(), "pupil").pivotY, 1e-5F);
        assertEquals(0.5F, find(face.root(), "eye_white").pivotX, 1e-5F);
        assertEquals(1.0F, find(face.root(), "eye_white").pivotY, 1e-5F);
    }

    private static ModelPart find(ModelPart part, String id) {
        ModelPart direct = part.children.get(id);
        if (direct != null) return direct;
        for (ModelPart child : part.children.values()) {
            ModelPart found = find(child, id);
            if (found != null) return found;
        }
        return null;
    }

    @Test
    void visibleBoxesHidesOnlyThePartCuboids() throws Exception {
        String json = """
                {"id":"layers","textureSize":[64,64],"animations":[{"parent.visible_boxes":"0"}],
                 "submodels":[
                  {"id":"parent","boxes":[{"coordinates":[-1,0,-1,2,2,2],"uvNorth":[0,0,2,2]}],
                   "submodels":[{"id":"child","translate":[0,0,0.5],
                                 "boxes":[{"coordinates":[-0.5,0,-0.5,1,1,1],"uvNorth":[0,0,1,1]}]}]}
                ]}
                """;
        JpmFaceModel face = new JpmFaceModel(JpmParser.parseText(json));
        face.animate(new JpmEvalContext());
        ModelPart parent = face.root().children.get("parent");
        assertNotNull(parent);
        assertTrue(parent.cuboids.isEmpty(), "visible_boxes=0 must hide only the parent boxes");
        assertTrue(parent.visible, "the part and its children stay visible");
        assertEquals(1, parent.children.get("child").cuboids.size());
    }

    @Test
    void sizeAddPlacesPupilInFrontOfEyeWhite() throws Exception {
        String json = """
                {"id":"layers","textureSize":[64,64],"submodels":[
                  {"id":"eye_white","boxes":[{"coordinates":[-2,0,-1.002,4,1,0.002],"uvNorth":[0,0,4,1]}]},
                  {"id":"pupil","boxes":[{"coordinates":[-1,0,-1,1,1,0],"uvNorth":[0,0,2,1],"sizeAdd":0.004}]}
                ]}
                """;
        JpmFaceModel face = new JpmFaceModel(JpmParser.parseText(json));
        float whiteFront = minZ(face.root().children.get("eye_white"));
        float pupilFront = minZ(face.root().children.get("pupil"));
        assertTrue(pupilFront < whiteFront, "pupil must be in front of eye white");
    }

    private static float minZ(ModelPart part) {
        float min = Float.POSITIVE_INFINITY;
        for (ModelPart.Cuboid cuboid : part.cuboids) {
            for (ModelPart.Quad quad : cuboid.sides) {
                for (ModelPart.Vertex vertex : quad.vertices) {
                    min = Math.min(min, vertex.pos.z());
                }
            }
        }
        return min;
    }
}
