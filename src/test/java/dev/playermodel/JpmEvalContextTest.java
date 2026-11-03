package dev.playermodel;

import dev.playermodel.jpm.JpmEvalContext;
import dev.playermodel.jpm.JpmExpressionParser;
import dev.playermodel.jpm.JpmModel;
import dev.playermodel.jpm.JpmParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Evaluator-only tests for EMF-compatible context values and selectors. */
class JpmEvalContextTest {
    @Test
    void exposesStandardContextValuesAndAliases() throws Exception {
        JpmEvalContext context = new JpmEvalContext();
        context.age = 12;
        context.headYaw = 25;
        context.headPitch = -12;
        context.limbSpeed = 0.2;
        context.limbSwing = 0.4;
        context.hurtTime = 3;
        context.distance = 8;
        context.camera = 5;
        context.frame = 7;
        context.tick = 42;
        context.time = 9.5;
        context.health = 17;
        context.air = 200;
        context.id = 99;
        context.hurt = true;
        context.isSneaking = true;
        context.isSprinting = false;
        context.isSwimming = true;
        context.isOnGround = true;
        context.isRiding = false;
        context.isChild = true;
        context.isInvisible = true;

        assertEquals(12, eval(context, "age"), 1e-9);
        assertEquals(25, eval(context, "head_yaw"), 1e-9);
        assertEquals(25, eval(context, "headYaw"), 1e-9);
        assertEquals(-12, eval(context, "head_pitch"), 1e-9);
        assertEquals(0.2, eval(context, "limb_speed"), 1e-9);
        assertEquals(0.4, eval(context, "limbSwing"), 1e-9);
        assertEquals(3, eval(context, "hurt_time"), 1e-9);
        assertEquals(8, eval(context, "distance"), 1e-9);
        assertEquals(5, eval(context, "camera"), 1e-9);
        assertEquals(7, eval(context, "frame"), 1e-9);
        assertEquals(42, eval(context, "tick"), 1e-9);
        assertEquals(200, eval(context, "air"), 1e-9);
        assertEquals(1, eval(context, "is_hurt"), 1e-9);
        assertEquals(1, eval(context, "isHurt"), 1e-9);
        assertEquals(1, eval(context, "is_sneaking"), 1e-9);
        assertEquals(0, eval(context, "isSprinting"), 1e-9);
        assertEquals(1, eval(context, "is_swimming"), 1e-9);
        assertEquals(1, eval(context, "is_on_ground"), 1e-9);
        assertEquals(0, eval(context, "is_riding"), 1e-9);
        assertEquals(1, eval(context, "is_child"), 1e-9);
        assertEquals(1, eval(context, "is_invisible"), 1e-9);
        assertEquals(17, eval(context, "health"), 1e-9);
    }

    @Test
    void supportsPlayerEntityAndRenderAliases() throws Exception {
        JpmEvalContext context = new JpmEvalContext();
        context.health = 20;
        context.isSprinting = true;
        context.distance = 6;
        context.camera = 4;
        context.frame = 2;
        context.tick = 11;
        assertEquals(20, eval(context, "player.health"), 1e-9);
        assertEquals(1, eval(context, "entity.is_sprinting"), 1e-9);
        assertEquals(6, eval(context, "render.distance"), 1e-9);
        assertEquals(4, eval(context, "render.camera"), 1e-9);
        assertEquals(2, eval(context, "render.frame"), 1e-9);
        assertEquals(11, eval(context, "render.tick"), 1e-9);
        assertEquals(0, eval(context, "render.unknown"), 1e-9);
    }

    @Test
    void readsPartPropertiesAndThisSelector() throws Exception {
        String json = """
                {"id":"eval","textureSize":[64,64],"animations":[
                  {"part.pupil.tx":"this.ty + 1", "part.pupil.visible_boxes":"0"}
                ],"submodels":[
                  {"id":"head","translate":[0,1,0],"boxes":[]},
                  {"id":"pupil","translate":[0,0.5,0],"boxes":[]}
                ]}
                """;
        JpmModel model = JpmParser.parseText(json);
        JpmEvalContext context = new JpmEvalContext();
        model.animate(context);
        assertEquals(1.5, context.parts.get("pupil").tx, 1e-9);
        assertFalse(context.parts.get("pupil").visibleBoxes);
        assertEquals(0, context.value("part.pupil.visible_boxes"), 1e-9);
        assertEquals(1.5, context.value("part.pupil.tx"), 1e-9);
        assertNull(context.currentPart);
    }

    @Test
    void readsHeadBodyAliasesAndEarlierAnimationWrites() throws Exception {
        String json = """
                {"id":"eval","textureSize":[64,64],"animations":[
                  {"head.ry":"0.5"},
                  {"body.rz":"part.head.ry + 1"},
                  {"part.pupil.ty":"body.rz"}
                ],"submodels":[
                  {"id":"head","boxes":[]},
                  {"id":"body","boxes":[]},
                  {"id":"pupil","boxes":[]}
                ]}
                """;
        JpmModel model = JpmParser.parseText(json);
        JpmEvalContext context = new JpmEvalContext();
        model.animate(context);
        assertEquals(0.5, context.parts.get("head").ry, 1e-9);
        assertEquals(1.5, context.parts.get("body").rz, 1e-9);
        assertEquals(1.5, context.parts.get("pupil").ty, 1e-9);
    }

    @Test
    void randomStaysStablePerSeed() throws Exception {
        JpmEvalContext context = new JpmEvalContext();
        context.id = 3;
        double first = eval(context, "random(id)");
        double again = eval(context, "random(id)");
        assertEquals(first, again, 0);
        JpmEvalContext other = new JpmEvalContext();
        other.id = 4;
        assertNotEquals(first, eval(other, "random(id)"));
        assertTrue(first >= 0 && first < 1);
    }

    private static double eval(JpmEvalContext context, String source) throws Exception {
        return JpmExpressionParser.parse(source).eval(context);
    }
}
