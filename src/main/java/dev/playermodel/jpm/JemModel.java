package dev.playermodel.jpm;

import java.util.ArrayList;
import java.util.List;

/** A parsed JEM player model descriptor. */
public record JemModel(int textureWidth, int textureHeight, List<JemPart> models) {
    public JemModel {
        models = List.copyOf(models);
    }

    /** All JEM attachments, in file order. */
    public List<JemPart> attachments() {
        return models;
    }

    /** First attachment for a vanilla part (referenced or inline). */
    public JemPart forVanillaPart(String part) {
        for (JemPart model : models) {
            if (part.equalsIgnoreCase(model.part())) return model;
        }
        return null;
    }

    /** All attachments targeting a vanilla part, in file order. */
    public List<JemPart> forVanillaParts(String part) {
        List<JemPart> result = new ArrayList<>();
        for (JemPart model : models) {
            if (part.equalsIgnoreCase(model.part())) result.add(model);
        }
        return List.copyOf(result);
    }

    /**
     * Returns the inline model declared for a vanilla part, or null when the part only has a
     * referenced JPM. This is the tree a renderer should build for e.g. {@code head}.
     */
    public JpmModel inlineForVanillaPart(String part) {
        for (JemPart model : models) {
            if (part.equalsIgnoreCase(model.part()) && model.hasInlineModel()) return model.inline();
        }
        return null;
    }

    /** First referenced JPM model, used by the legacy single-face pathway. */
    public JpmModel face() {
        for (JemPart model : models) {
            if (model.jpm() != null) return model.jpm();
        }
        return null;
    }
}
