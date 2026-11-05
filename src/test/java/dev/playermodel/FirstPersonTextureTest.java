package dev.playermodel;

import dev.playermodel.client.AppearanceManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirstPersonTextureTest {
    @Test
    void inventoryPreviewUsesModelTextureEvenInFirstPerson() {
        assertFalse(AppearanceManager.usesVanillaFirstPersonTexture(true, true, true),
                "screen previews must use the model texture");
    }

    @Test
    void inWorldFirstPersonArmKeepsVanillaSkin() {
        assertTrue(AppearanceManager.usesVanillaFirstPersonTexture(true, true, false));
    }

    @Test
    void otherPlayersAndThirdPersonAlwaysUseModelTexture() {
        assertFalse(AppearanceManager.usesVanillaFirstPersonTexture(false, true, false));
        assertFalse(AppearanceManager.usesVanillaFirstPersonTexture(true, false, false));
    }
}
