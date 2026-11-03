package dev.playermodel.jpm;

/** A compiled JPM animation expression. */
@FunctionalInterface
public interface JpmExpression {
    double eval(JpmEvalContext context);
}
