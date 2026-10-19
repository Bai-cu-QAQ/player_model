package dev.playermodel.client;

import dev.playermodel.PlayerModelMod;
import dev.playermodel.model.ModelBundle;
import dev.playermodel.net.Chunking;
import dev.playermodel.net.Payloads.*;
import dev.playermodel.runtime.Appearance;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Client side of the sync protocol. Handlers run on the render thread. */
public final class PlayerModelClientNetworking {
    private static boolean syncAvailable;
    private static int maxModelBytes = 4 * 1024 * 1024;
    private static final Map<String, Chunking.Assembler> DOWNLOADS = new HashMap<>();
    private static final Set<String> REQUESTED = new HashSet<>();

    private PlayerModelClientNetworking() {
    }

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onJoin(client, handler));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnect());

        ClientPlayNetworking.registerGlobalReceiver(HelloS2C.ID, (payload, context) -> onHello(payload));
        ClientPlayNetworking.registerGlobalReceiver(StateS2C.ID, (payload, context) -> onState(payload));
        ClientPlayNetworking.registerGlobalReceiver(SnapshotS2C.ID, (payload, context) -> onSnapshot(payload));
        ClientPlayNetworking.registerGlobalReceiver(NeedModelS2C.ID, (payload, context) -> onNeedModel(payload));
        ClientPlayNetworking.registerGlobalReceiver(UploadResultS2C.ID, (payload, context) -> onUploadResult(payload));
        ClientPlayNetworking.registerGlobalReceiver(ModelBeginS2C.ID, (payload, context) -> onModelBegin(payload));
        ClientPlayNetworking.registerGlobalReceiver(ModelChunkS2C.ID, (payload, context) -> onModelChunk(payload));
        ClientPlayNetworking.registerGlobalReceiver(ModelEndS2C.ID, (payload, context) -> onModelEnd(payload));
        ClientPlayNetworking.registerGlobalReceiver(ModelDeniedS2C.ID, (payload, context) -> onModelDenied(payload));
    }

    private static void onJoin(MinecraftClient client, ClientPlayNetworkHandler handler) {
        PlayerModelClient.appearanceManager().setServerSyncAvailable(false);
        DOWNLOADS.clear();
        REQUESTED.clear();
        syncAvailable = false;
        if (ClientPlayNetworking.canSend(HelloC2S.ID)) {
            ClientPlayNetworking.send(new HelloC2S(dev.playermodel.net.Payloads.PROTOCOL));
        } else {
            PlayerModelMod.LOG.info("Server has no Player Model sync; client-local mode");
        }
    }

    private static void onDisconnect() {
        syncAvailable = false;
        PlayerModelClient.appearanceManager().clearRemote();
        PlayerModelClient.appearanceManager().setServerSyncAvailable(false);
        DOWNLOADS.clear();
        REQUESTED.clear();
    }

    private static void onHello(HelloS2C payload) {
        if (payload.protocol() != dev.playermodel.net.Payloads.PROTOCOL) {
            syncAvailable = false;
            message("message.playermodel.protocol_mismatch");
            return;
        }
        maxModelBytes = payload.maxModelBytes();
        syncAvailable = payload.enabled();
        PlayerModelClient.appearanceManager().setServerSyncAvailable(syncAvailable);
        if (syncAvailable) {
            sendOwnState();
        }
    }

    private static void onSnapshot(SnapshotS2C payload) {
        for (SnapshotS2C.Entry entry : payload.entries()) {
            PlayerModelClient.appearanceManager().setRemote(entry.uuid(),
                    new Appearance(entry.hash(), entry.enabled(), entry.name()));
            if (entry.enabled()) {
                requestIfMissing(entry.hash());
            }
        }
    }

    private static void onState(StateS2C payload) {
        PlayerModelClient.appearanceManager().setRemote(payload.uuid(),
                new Appearance(payload.hash(), payload.enabled(), payload.name()));
        if (payload.enabled()) {
            requestIfMissing(payload.hash());
        }
    }

    private static void onNeedModel(NeedModelS2C payload) {
        String hash = payload.hash();
        ModelBundle bundle = PlayerModelClient.registry().bundle(hash);
        if (bundle == null) {
            message("message.playermodel.upload_missing");
            return;
        }
        if (bundle.bytes.length > maxModelBytes) {
            message("message.playermodel.model_too_large");
            return;
        }
        var chunks = Chunking.split(bundle.bytes);
        ClientPlayNetworking.send(new UploadBeginC2S(hash, bundle.bytes.length, chunks.size()));
        for (int i = 0; i < chunks.size(); i++) {
            ClientPlayNetworking.send(new UploadChunkC2S(hash, i, chunks.get(i)));
        }
        ClientPlayNetworking.send(new UploadEndC2S(hash));
    }

    private static void onUploadResult(UploadResultS2C payload) {
        if (payload.ok()) {
            PlayerModelMod.LOG.info("Model {} uploaded", payload.hash().substring(0, 8));
            sendOwnState();
        } else {
            message("message.playermodel.upload_failed");
            PlayerModelMod.LOG.warn("Model upload failed: {}", payload.message());
        }
    }

    private static void onModelBegin(ModelBeginS2C payload) {
        DOWNLOADS.put(payload.hash(), new Chunking.Assembler(payload.totalLength(), payload.chunkCount()));
    }

    private static void onModelChunk(ModelChunkS2C payload) {
        Chunking.Assembler assembler = DOWNLOADS.get(payload.hash());
        if (assembler != null) {
            assembler.accept(payload.index(), payload.data());
        }
    }

    private static void onModelEnd(ModelEndS2C payload) {
        Chunking.Assembler assembler = DOWNLOADS.remove(payload.hash());
        REQUESTED.remove(payload.hash());
        if (assembler == null || !assembler.isComplete()) {
            return;
        }
        try {
            byte[] bytes = assembler.finish();
            ModelBundle bundle = ModelBundle.fromBytes(bytes);
            if (!bundle.hash.equals(payload.hash())) {
                return;
            }
            PlayerModelClient.registry().saveCache(bundle);
            PlayerModelMod.LOG.info("Downloaded player model {} ({} bytes)", bundle.shortHash(), bytes.length);
        } catch (Exception e) {
            PlayerModelMod.LOG.warn("Invalid downloaded model {}", payload.hash(), e);
        }
    }

    private static void onModelDenied(ModelDeniedS2C payload) {
        REQUESTED.remove(payload.hash());
        PlayerModelMod.LOG.warn("Server denied model {}: {}", payload.hash(), payload.reason());
    }

    private static void requestIfMissing(String hash) {
        if (!syncAvailable || hash == null || hash.isBlank()) {
            return;
        }
        if (PlayerModelClient.registry().bundle(hash) != null) {
            return;
        }
        if (!REQUESTED.add(hash)) {
            return;
        }
        ClientPlayNetworking.send(new RequestModelC2S(hash));
    }

    /** Sends the local player's current selection to the server (if sync is available). */
    public static void onLocalModelChanged() {
        if (syncAvailable) {
            sendOwnState();
        }
    }

    private static void sendOwnState() {
        if (!syncAvailable) {
            return;
        }
        AppearanceManager manager = PlayerModelClient.appearanceManager();
        boolean enabled = manager.selfEnabled() && manager.activeHash() != null;
        String hash = enabled ? manager.activeHash() : "";
        String name = enabled ? PlayerModelClient.registry().nameForHash(hash) : "";
        ClientPlayNetworking.send(new StateC2S(enabled, hash, name));
    }

    private static void message(String key) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.translatable(key), true);
        }
    }

    public static boolean syncAvailable() {
        return syncAvailable;
    }
}
