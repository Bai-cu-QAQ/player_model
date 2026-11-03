package dev.playermodel.jpm;

import java.util.ArrayList;
import java.util.List;

/** Parsed OptiFine/EMF JPM model. */
public final class JpmModel {
    public final String id;
    public final int textureWidth;
    public final int textureHeight;
    public final String texture;
    public final List<JpmPart> roots;
    public final List<JpmAnimation> animations;

    public JpmModel(String id, int textureWidth, int textureHeight, String texture,
                    List<JpmPart> roots, List<JpmAnimation> animations) {
        this.id = id;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.texture = texture == null ? "" : texture;
        this.roots = List.copyOf(roots);
        this.animations = List.copyOf(animations);
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
        for (JpmPart part : flatten()) {
            JpmEvalContext.PartState state = context.part(part.id);
            state.tx = part.translate[0];
            state.ty = part.translate[1];
            state.tz = part.translate[2];
            state.rx = part.rotate[0];
            state.ry = part.rotate[1];
            state.rz = part.rotate[2];
            state.sx = part.scale;
            state.sy = part.scale;
            state.sz = part.scale;
            state.visible = true;
        }
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
