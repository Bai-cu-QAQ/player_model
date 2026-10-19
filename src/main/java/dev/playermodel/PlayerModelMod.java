package dev.playermodel;

import dev.playermodel.net.Networking;
import dev.playermodel.server.ServerSync;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PlayerModelMod implements ModInitializer {
    public static final String MOD_ID = "playermodel";
    public static final Logger LOG = LoggerFactory.getLogger("PlayerModel");

    @Override
    public void onInitialize() {
        Networking.registerPayloads();
        Networking.registerServerReceivers();

        ServerLifecycleEvents.SERVER_STARTING.register(ServerSync::start);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ServerSync.stop());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ServerSync.onJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ServerSync.onDisconnect(handler.player));

        LOG.info("Player Model Importer common init");
    }
}
