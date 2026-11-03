package dev.playermodel.jpm;

import java.util.ArrayList;
import java.util.List;

/** Parsed OptiFine/EMF JPM model. */
public final class JpmModel {
    public final String id;
    public final int textureWidth;
    public final int textureHeight;
    public final List<JpmPart> roots;
    public final List<JpmAnimation> animations;

    public JpmModel(String id, int textureWidth, int textureHeight,
                    List<JpmPart> roots, List<JpmAnimation> animations) {
        this.id = id;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.roots = List.copyOf(roots);
        this.animations = List.copyOf(animations);
    }

    public JpmPart find(String partId) {
        for (JpmPart root : roots) {
            JpmPart found = root.find(partId);
            if (found != null) return found;
        }
        return null;
    }

    public List<JpmPart> flatten() {
        List<JpmPart> out = new ArrayList<>();
        for (JpmPart root : roots) root.collect(out);
        return List.copyOf(out);
    }

    public int boxCount() {
        int count = 0;
        for (JpmPart part : flatten()) count += part.boxes.size();
        return count;
    }

    public void animate(JpmEvalContext context) {
        for (JpmAnimation animation : animations) {
            for (var entry : animation.expressions.entrySet()) {
                String key = entry.getKey();
                double value = entry.getValue().eval(context);
                if (!Double.isFinite(value)) continue;
                if (key.startsWith("var.")) {
                    context.variables.put(key, value);
                } else {
                    int dot = key.lastIndexOf('.');
                    if (dot > 0) {
                        context.part(key.substring(0, dot))
                                .set(key.substring(dot + 1), value);
                    }
                }
            }
        }
    }
}
