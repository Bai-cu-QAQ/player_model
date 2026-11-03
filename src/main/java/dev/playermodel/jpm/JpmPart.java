package dev.playermodel.jpm;

import java.util.ArrayList;
import java.util.List;

/** A JPM model/submodel node. Coordinates are local to the parent part. */
public final class JpmPart {
    public final String id;
    public final float[] translate;
    public final String invertAxis;
    public final String mirrorTexture;
    public final List<JpmBox> boxes;
    public final List<JpmPart> children;

    public JpmPart(String id, float[] translate, String invertAxis, String mirrorTexture,
                   List<JpmBox> boxes, List<JpmPart> children) {
        this.id = id;
        this.translate = translate.clone();
        this.invertAxis = invertAxis;
        this.mirrorTexture = mirrorTexture;
        this.boxes = List.copyOf(boxes);
        this.children = List.copyOf(children);
    }

    public JpmPart find(String partId) {
        if (id.equals(partId)) return this;
        for (JpmPart child : children) {
            JpmPart found = child.find(partId);
            if (found != null) return found;
        }
        return null;
    }

    public void collect(List<JpmPart> out) {
        out.add(this);
        for (JpmPart child : children) child.collect(out);
    }

    public java.util.List<JpmPart> flatten() {
        List<JpmPart> out = new ArrayList<>();
        collect(out);
        return List.copyOf(out);
    }
}
