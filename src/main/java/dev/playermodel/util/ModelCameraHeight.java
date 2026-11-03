package dev.playermodel.util;

/** Pure camera target calculation; interpolation and camera transforms remain owned by Minecraft. */
public final class ModelCameraHeight {
    private ModelCameraHeight() {}

    /**
     * Preserve the current vanilla pose/scale ratio while replacing the standing model baseline.
     * For example, a half-height model receives half the vanilla standing AND crouching eye heights.
     * Do not add a fixed standing offset: that can push crawling eyes below the floor.
     */
    public static float adapt(float currentVanillaEye, float baseStandingEye, float modelStandingEye) {
        if (!Float.isFinite(currentVanillaEye) || currentVanillaEye < 0
                || !Float.isFinite(baseStandingEye) || baseStandingEye <= 0
                || !Float.isFinite(modelStandingEye) || modelStandingEye <= 0) {
            return currentVanillaEye;
        }
        float adapted = currentVanillaEye * (modelStandingEye / baseStandingEye);
        return Float.isFinite(adapted) ? adapted : currentVanillaEye;
    }
}
