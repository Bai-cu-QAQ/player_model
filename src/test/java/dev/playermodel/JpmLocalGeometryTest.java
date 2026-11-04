package dev.playermodel;

import dev.playermodel.jpm.JpmEvalContext;
import dev.playermodel.jpm.JpmParser;
import dev.playermodel.runtime.JpmFaceModel;
import net.minecraft.client.model.ModelPart;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JpmLocalGeometryTest {
    @Test
    void boxesStayLocalAndAnimationsRemainAbsolute() throws Exception {
        JpmFaceModel face = new JpmFaceModel(JpmParser.parseText("""
                {"textureSize":[64,64],"submodels":[
                  {"id":"eye","translate":[1,2,-4],"boxes":[
                    {"coordinates":[0,0,-0.002,2,1,0.002],"uvNorth":[4,7,5,8]}]}
                ],"animations":[{"eye.ty":"3","eye.sy":"2"}]}
                """));
        ModelPart eye = face.root().children.get("eye");
        assertEquals(0, minY(eye), 1e-6, "JPM boxes must not subtract the part origin");
        face.animate(new JpmEvalContext());
        assertEquals(3, eye.originY, 1e-6, "JPM animation values are absolute");
        assertEquals(2, eye.yScale, 1e-6);
        assertEquals(0, minY(eye), 1e-6);
        assertEquals(3, eye.originY + eye.yScale * minY(eye), 1e-6);
        var quad = eye.cuboids.get(0).sides[0];
        assertEquals(5f / 64, quad.vertices()[0].u(), 1e-6);
        assertEquals(7f / 64, quad.vertices()[0].v(), 1e-6);
    }

    @Test
    void explicitWestUvDrawsOnTheEastFacingPlane() throws Exception {
        JpmFaceModel face = new JpmFaceModel(JpmParser.parseText("""
                {"textureSize":[64,64],"submodels":[
                  {"id":"box","boxes":[
                    {"coordinates":[0,0,0,2,1,0.002],"uvWest":[0,0,2,1]}]}
                ]}
                """));
        ModelPart box = face.root().children.get("box");
        var quad = box.cuboids.get(0).sides[0];
        assertEquals(1, quad.direction().x(), 1e-6, "explicit uvWest must draw the +X face");
        for (var vertex : quad.vertices()) {
            assertEquals(2, vertex.x(), 1e-6, "the +X face must sit on the max X plane");
        }
    }

    private static float minY(ModelPart part) {
        float value = Float.POSITIVE_INFINITY;
        for (var box : part.cuboids) for (var face : box.sides)
            for (var vertex : face.vertices()) value = Math.min(value, vertex.y());
        return value;
    }
}
