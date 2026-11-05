package dev.playermodel;

import dev.playermodel.util.ModelCameraHeight;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelCameraHeightTest {
    @Test void standingUsesModelEyeBaseline() {
        assertEquals(.81F, ModelCameraHeight.adapt(1.62F, 1.62F, .81F), 1e-6F);
    }
    @Test void crouchingAndCrawlingKeepVanillaPoseRatios() {
        assertEquals(.635F, ModelCameraHeight.adapt(1.27F, 1.62F, .81F), 1e-6F);
        assertEquals(.2F, ModelCameraHeight.adapt(.4F, 1.62F, .81F), 1e-6F);
        assertEquals(.1F, ModelCameraHeight.adapt(.2F, 1.62F, .81F), 1e-6F);
    }
    @Test void vanillaSizedModelLeavesAllPoseHeightsUnchanged() {
        for (float eye : new float[]{1.62F, 1.27F, .4F, .2F, 0}) {
            assertEquals(eye, ModelCameraHeight.adapt(eye, 1.62F, 1.62F), 1e-6F);
        }
    }
    @Test void entityScaleAndIntermediatePoseHeightsAreNotDiscarded() {
        assertEquals(1.62F, ModelCameraHeight.adapt(3.24F, 1.62F, .81F), 1e-6F);
        assertEquals(.5F, ModelCameraHeight.adapt(1F, 1.62F, .81F), 1e-6F);
    }
    @Test void invalidModelMeasurementsFallBackToVanilla() {
        assertEquals(1.27F, ModelCameraHeight.adapt(1.27F, 0, .81F), 1e-6F);
        assertEquals(1.27F, ModelCameraHeight.adapt(1.27F, 1.62F, Float.NaN), 1e-6F);
    }
}
