package dev.playermodel.jpm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** One ordered JPM animation block. Keys are variables or part properties. */
public final class JpmAnimation {
    public final Map<String, JpmExpression> expressions;

    public JpmAnimation(Map<String, JpmExpression> expressions) {
        this.expressions = Collections.unmodifiableMap(new LinkedHashMap<>(expressions));
    }
}
