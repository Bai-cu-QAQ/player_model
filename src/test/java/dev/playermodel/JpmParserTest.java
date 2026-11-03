package dev.playermodel;

import dev.playermodel.jpm.JemModel;
import dev.playermodel.jpm.JemParser;
import dev.playermodel.jpm.JpmEvalContext;
import dev.playermodel.jpm.JpmExpressionParser;
import dev.playermodel.jpm.JpmModel;
import dev.playermodel.jpm.JpmParser;
import dev.playermodel.runtime.JpmFaceModel;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JpmParserTest {
    private static final Path JPM = Path.of(".tools/JustExpressions/assets/minecraft/emf/cem/player_face.jpm");
    private static final Path JEM = Path.of(".tools/JustExpressions/assets/minecraft/emf/cem/player.jem");

    @Test
    void parsesExpressionsFunctionsAndVariables() throws Exception {
        var context = new JpmEvalContext();
        context.age = 12;
        context.id = 0.25;
        assertEquals(1.0, JpmExpressionParser.parse("clamp(5, 0, 1)").eval(context), 1e-9);
        assertEquals(2.0, JpmExpressionParser.parse("if(1, 2, 3)").eval(context), 1e-9);
        assertEquals(Math.PI, JpmExpressionParser.parse("torad(180)").eval(context), 1e-9);
        assertEquals(1.0, JpmExpressionParser.parse("sin(pi / 2)").eval(context), 1e-9);
        assertEquals(1.0, JpmExpressionParser.parse("1 && 2 > 1").eval(context), 1e-9);
        assertEquals(20.0, JpmExpressionParser.parse("1 > 2 ? 10 : 20").eval(context), 1e-9);
        double random = JpmExpressionParser.parse("random(id)").eval(context);
        assertTrue(random >= 0 && random < 1);
    }

    @Test
    void parsesAndAnimatesRealJustExpressionsFace() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(JPM), "local JustExpressions resource pack is unavailable");
        JpmModel model = JpmParser.parse(JPM);
        assertEquals("player_face", model.id);
        assertEquals(64, model.textureWidth);
        assertEquals(64, model.textureHeight);
        assertEquals(130, model.boxCount());
        assertEquals(11, model.animations.size());
        assertEquals(336, model.flatten().size());

        JpmEvalContext context = new JpmEvalContext();
        context.age = 123.0;
        context.id = 0.37;
        context.headYaw = 25.0;
        context.headPitch = -12.0;
        context.limbSpeed = 0.2;
        context.limbSwing = 0.4;
        context.distance = 8.0;
        context.hurt = false;
        model.animate(context);

        assertFalse(context.variables.isEmpty());
        assertTrue(context.variables.values().stream().allMatch(Double::isFinite));
        for (var part : context.parts.values()) {
            assertTrue(Double.isFinite(part.tx) && Double.isFinite(part.ty) && Double.isFinite(part.tz));
            assertTrue(Double.isFinite(part.sx) && Double.isFinite(part.sy) && Double.isFinite(part.sz));
        }
        var face = new JpmFaceModel(model);
        assertEquals(4, face.root().children.size());
        face.apply(context);
    }

    @Test
    void parsesJemAndResolvesFaceModel() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(JEM), "local JustExpressions resource pack is unavailable");
        JemModel jem = JemParser.parse(JEM);
        assertEquals(64, jem.textureWidth());
        assertEquals(13, jem.models().size());
        assertNotNull(jem.face());
        assertEquals("player_face", jem.face().id);
        assertNotNull(jem.forVanillaPart("head"));
    }
}
