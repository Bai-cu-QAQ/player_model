package dev.playermodel.neoforge;

import dev.playermodel.jpm.JpmEvalContext;
import dev.playermodel.jpm.JpmExpressionParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AngleExpressionTest {
    private final JpmEvalContext context = new JpmEvalContext();

    @Test
    void preservesFloatWrappingBoundariesAndMultipleTurns() throws Exception {
        assertEquals(-180.0, evaluate("wrapdeg(180)"));
        assertEquals(-180.0, evaluate("wrapdeg(-180)"));
        assertEquals(-180.0, evaluate("wrapdeg(540)"));
        assertEquals(179.0, evaluate("wrapdeg(-541)"));
        assertEquals(-179.0, evaluate("wrapdeg(541)"));
        assertEquals(-1.25, evaluate("wrapdeg(358.75)"));
        assertEquals(20.0, evaluate("degdiff(10, 350)"));
        assertEquals(-20.0, evaluate("degdiff(350, 10)"));
    }

    @Test
    void preservesRadiansAndNonFiniteBehavior() throws Exception {
        assertEquals(-Math.PI, evaluate("wraprad(pi)"), 1.0e-6);
        assertEquals(-Math.PI, evaluate("wraprad(-pi)"), 1.0e-6);
        assertEquals(Math.PI / 2.0, evaluate("raddiff(pi, pi / 2)"), 1.0e-6);
        context.age = Double.POSITIVE_INFINITY;
        assertTrue(Double.isNaN(evaluate("wrapdeg(age)")));
        context.age = Double.NaN;
        assertTrue(Double.isNaN(evaluate("wrapdeg(age)")));
    }

    private double evaluate(String expression) throws Exception {
        return JpmExpressionParser.parse(expression).eval(context);
    }
}
