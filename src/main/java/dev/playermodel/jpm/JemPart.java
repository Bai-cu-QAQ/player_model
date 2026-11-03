package dev.playermodel.jpm;

/**
 * One JEM model attachment.
 *
 * <p>{@code jpm} is a referenced {@code .jpm} model (for example {@code player_face.jpm});
 * {@code inline} is a model tree declared directly inside the JEM entry. Either may be null.
 * Inline entries use the same coordinate/invert semantics as JPM submodels.</p>
 */
public record JemPart(String part, String id, String model, JpmModel jpm,
                      boolean attach, String texture, int textureWidth, int textureHeight,
                      JpmModel inline) {
    /** True when this entry contains boxes/submodels declared inline. */
    public boolean hasInlineModel() {
        return inline != null;
    }

    /** True when this entry references an external JPM file. */
    public boolean hasReferencedModel() {
        return jpm != null;
    }
}
