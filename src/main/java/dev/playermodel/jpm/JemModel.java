package dev.playermodel.jpm;

import java.util.List;

/** A parsed JEM player model descriptor. */
public record JemModel(int textureWidth, int textureHeight, List<JemPart> models) {
    public JemModel {
        models = List.copyOf(models);
    }

    public JemPart forVanillaPart(String part) {
        for (JemPart model : models) {
            if (part.equalsIgnoreCase(model.part())) return model;
        }
        return null;
    }

    public JpmModel face() {
        for (JemPart model : models) {
            if (model.jpm() != null) return model.jpm();
        }
        return null;
    }
}
