package dev.playermodel.jpm;

import java.util.List;

/** One JEM model attachment. A null jpm means a vanilla part reference. */
public record JemPart(String part, String id, String model, JpmModel jpm) {
    public static JemPart vanilla(String part, String id) {
        return new JemPart(part, id, null, null);
    }
}
