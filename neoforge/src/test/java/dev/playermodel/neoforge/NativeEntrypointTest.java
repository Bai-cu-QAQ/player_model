package dev.playermodel.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeEntrypointTest {
    @Test
    void exposesNativeModAnnotationAndEventBusConstructor() throws Exception {
        Mod annotation = PlayerModelNeoForge.class.getAnnotation(Mod.class);
        assertNotNull(annotation);
        assertEquals("playermodel", annotation.value());
        assertNotNull(PlayerModelNeoForge.class.getConstructor(IEventBus.class));
    }

    @Test
    void providesExpandedNativeDevelopmentMetadataOnly() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        try (var input = loader.getResourceAsStream("META-INF/neoforge.mods.toml")) {
            assertNotNull(input);
            String metadata = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(metadata.contains("modLoader=\"javafml\""));
            assertTrue(metadata.contains("1.0.0-dev-mc1.21.1"));
            assertTrue(metadata.contains("NOT implemented"));
            assertFalse(metadata.contains("${"));
        }
        assertNull(loader.getResource("fabric.mod.json"));
        assertNull(loader.getResource("playermodel.mixins.json"));
        assertNull(loader.getResource("playermodel.accesswidener"));
    }
}
