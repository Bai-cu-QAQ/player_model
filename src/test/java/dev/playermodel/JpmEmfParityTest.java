package dev.playermodel;

import dev.playermodel.jpm.*;
import dev.playermodel.runtime.JpmFaceModel;
import net.minecraft.client.model.ModelPart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class JpmEmfParityTest {
    @Test
    void boxesStayLocalToPartEvenWithAnimatedPivotAndScale() throws Exception {
        JpmFaceModel face = new JpmFaceModel(JpmParser.parseText("""
                {"textureSize":[64,64],"submodels":[
                  {"id":"eye","translate":[1,2,-4],"boxes":[
                    {"coordinates":[0,0,-0.002,2,1,0.002],"uvNorth":[4,7,5,8]}]}
                ],"animations":[{"eye.ty":"3","eye.sy":"2"}]}
                """));
        ModelPart eye = face.root().children.get("eye");
        assertEquals(0, minY(eye), 1e-6, "JPM coordinates must not subtract the part pivot");
        face.animate(new JpmEvalContext());
        assertEquals(3, eye.pivotY, 1e-6);
        assertEquals(2, eye.yScale, 1e-6);
        assertEquals(0, minY(eye), 1e-6, "animation must not rebase box vertices");
        // Actual top edge: pivot + scale * local vertex = 3, not 3 + 2 * -2 = -1.
        assertEquals(3, eye.pivotY + eye.yScale * minY(eye), 1e-6);
        var quad = eye.cuboids.get(0).sides[0];
        assertEquals(5f / 64, quad.vertices[0].u, 1e-6);
        assertEquals(7f / 64, quad.vertices[0].v, 1e-6);
    }

    @Test
    void realPackKeepsAllEyeGroupsAtEveryDistanceAndUsesDeclaredUvs() throws Exception {
        Path file = Path.of(".tools/JustExpressions/assets/minecraft/emf/cem/player_face.jpm");
        Assumptions.assumeTrue(Files.isRegularFile(file));
        JpmModel data = JpmParser.parse(file);
        JpmFaceModel face = new JpmFaceModel(data);
        JpmEvalContext context = new JpmEvalContext();
        context.age = 123; context.id = 37; context.headPitch = 12;
        for (double distance : new double[]{0, 4, 25, 64}) {
            context.distance = distance;
            face.animate(context);
            for (int i : new int[]{1, 5, 9, 10, 11, 16}) {
                ModelPart white = find(face.root(), "r_eye_white" + i);
                assertNotNull(white);
                assertTrue(white.visible, "No Java variant filtering: eye " + i + " at " + distance);
                assertFalse(white.cuboids.isEmpty());
                assertEquals(context.parts.get("r_eye_white" + i).sy, white.yScale, 1e-6);
            }
            assertTrue(find(face.root(), "r_pupil12").visible);
            assertTrue(find(face.root(), "2by2").zScale > 0);
        }
        var white = find(face.root(), "r_eye_white1");
        // Inverted JPM box y=-1..0 is LOCAL; pivot y=1 is applied by ModelPart later.
        assertEquals(-1, minY(white), 1e-6);
        var quad = white.cuboids.get(0).sides[0];
        // First explicit face is NORTH for this box. No +5 skin/atlas translation.
        assertEquals(5f / 64, quad.vertices[0].u, 1e-6);
        assertEquals(7f / 64, quad.vertices[0].v, 1e-6);
    }

    private static float minY(ModelPart part) {
        float value = Float.POSITIVE_INFINITY;
        for (var box : part.cuboids) for (var face : box.sides) for (var vertex : face.vertices)
            value = Math.min(value, vertex.pos.y());
        return value;
    }

    private static ModelPart find(ModelPart part, String id) {
        var direct = part.children.get(id);
        if (direct != null) return direct;
        for (var child : part.children.values()) {
            var found = find(child, id);
            if (found != null) return found;
        }
        return null;
    }
}
