package dev.playermodel.jpm;

import java.util.List;

/** A JPM model/submodel node. Coordinates are local to the parent part. */
public final class JpmPart {
    public final String id;
    public final float[] translate;
    public final float[] rotate;
    public final float scale;
    public final List<JpmBox> boxes;
    public final List<JpmPart> children;

    public JpmPart(String id, float[] translate, float[] rotate, float scale,
                   List<JpmBox> boxes, List<JpmPart> children) {
        this.id = id;
        this.translate = translate.clone();
        this.rotate = rotate.clone();
        this.scale = scale;
        this.boxes = List.copyOf(boxes);
        this.children = List.copyOf(children);
    }

    public void collect(List<JpmPart> out) {
        out.add(this);
        for (JpmPart child : children) child.collect(out);
    }
}
