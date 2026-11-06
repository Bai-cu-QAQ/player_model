package dev.playermodel.neoforge;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(PlayerModelNeoForge.MOD_ID)
public final class PlayerModelNeoForge {
    public static final String MOD_ID = "playermodel";
    private static final Logger LOGGER = LogUtils.getLogger();

    public PlayerModelNeoForge(IEventBus modBus) {
        modBus.addListener(this::commonSetup);
        LOGGER.warn("Player Model NeoForge DEVELOPMENT PORT: bootstrap only; custom model rendering, UI and networking are not implemented.");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Player Model native NeoForge common setup reached (Minecraft 1.21.1, Mojang mappings).");
    }
}
