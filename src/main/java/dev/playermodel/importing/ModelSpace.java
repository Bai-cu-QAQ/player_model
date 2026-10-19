package dev.playermodel.importing;

/** Coordinate space of the source Blockbench project. */
public enum ModelSpace {
    /** Vanilla model space: head pivot at y=0, feet at y=24. */
    VANILLA,
    /** Blockbench player space: feet at y=0, head at y=24 (Y up). */
    BLOCKBENCH;

    public float y(float rawY) {
        return this == BLOCKBENCH ? 24.0F - rawY : rawY;
    }
}
