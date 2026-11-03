package dev.playermodel.jpm;

/** One JEM model attachment. A null jpm means a vanilla part reference. */
public record JemPart(String part, String id, String model, JpmModel jpm) {
}
