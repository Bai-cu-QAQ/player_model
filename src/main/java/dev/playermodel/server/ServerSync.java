package dev.playermodel.server;

import dev.playermodel.PlayerModelMod;
import dev.playermodel.net.Chunking;
import dev.playermodel.net.Payloads;
import dev.playermodel.net.Payloads.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server side of the protocol: state broadcast plus chunked, hash-verified model transfer. */
public final class ServerSync {
    private static ServerConfig config;
    private static ServerStore store;
    private static MinecraftServer server;
    private static final Map<UUID, Upload> UPLOADS = new HashMap<>();

    private ServerSync() {
    }

    public static synchronized void start(MinecraftServer minecraftServer) {
        server = minecraftServer;
        config = ServerConfig.load();
        store = new ServerStore(config);
        UPLOADS.clear();
        PlayerModelMod.LOG.info("Player Model server sync started ({} cached model(s))", store.modelCount());
    }

    public static synchronized void stop() {
        if (store != null) {
            store.saveStates();
        }
        store = null;
        config = null;
        server = null;
        UPLOADS.clear();
    }

    public static synchronized ServerStore store() {
        return store;
    }

    public static void onJoin(ServerPlayerEntity player) {
        if (!ready() || !ServerPlayNetworking.canSend(player, HelloS2C.ID)) {
            return;
        }
        sendHello(player);
        sendSnapshot(player);
    }

    public static void onDisconnect(ServerPlayerEntity player) {
        UPLOADS.remove(player.getUuid());
    }

    public static void onHello(ServerPlayerEntity player, HelloC2S payload) {
        if (!ready() || !ServerPlayNetworking.canSend(player, HelloS2C.ID)) {
            return;
        }
        if (payload.protocol() != Payloads.PROTOCOL) {
            ServerPlayNetworking.send(player, new HelloS2C(Payloads.PROTOCOL, false,
                    config.maxModelBytes, Chunking.CHUNK_SIZE));
            return;
        }
        sendHello(player);
        sendSnapshot(player);
    }

    private static void sendHello(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new HelloS2C(Payloads.PROTOCOL, config.enabled,
                config.maxModelBytes, Chunking.CHUNK_SIZE));
    }

    private static void sendSnapshot(ServerPlayerEntity player) {
        if (!config.enabled) {
            return;
        }
        List<SnapshotS2C.Entry> entries = new ArrayList<>();
        for (ServerStore.State state : store.snapshot()) {
            entries.add(new SnapshotS2C.Entry(state.uuid(), state.enabled(), state.hash(), state.name()));
        }
        ServerPlayNetworking.send(player, new SnapshotS2C(entries));
    }

    public static void onState(ServerPlayerEntity player, StateC2S payload) {
        if (!ready() || !config.enabled) {
            return;
        }
        String hash = payload.hash() == null ? "" : payload.hash().trim();
        if (!hash.isEmpty() && !validHash(hash)) {
            PlayerModelMod.LOG.warn("Rejected invalid model hash from {}", player.getGameProfile().getName());
            return;
        }
        String name = sanitizeName(payload.name());
        boolean enabled = payload.enabled() && !hash.isEmpty();
        store.setState(player.getUuid(), enabled, enabled ? hash : "", name);
        if (enabled && !store.hasModel(hash) && config.allowUploads
                && ServerPlayNetworking.canSend(player, NeedModelS2C.ID)) {
            ServerPlayNetworking.send(player, new NeedModelS2C(hash));
        }
        broadcastState(player.getUuid());
    }

    public static void broadcastState(UUID uuid) {
        if (!ready() || !config.enabled) {
            return;
        }
        ServerStore.State state = store.state(uuid);
        if (state == null) {
            return;
        }
        StateS2C payload = new StateS2C(state.uuid(), state.enabled(), state.hash(), state.name());
        for (ServerPlayerEntity target : server.getPlayerManager().getPlayerList()) {
            if (ServerPlayNetworking.canSend(target, StateS2C.ID)) {
                ServerPlayNetworking.send(target, payload);
            }
        }
    }

    public static void onUploadBegin(ServerPlayerEntity player, UploadBeginC2S payload) {
        if (!ready() || !config.enabled || !config.allowUploads) {
            return;
        }
        String hash = payload.hash();
        if (!validHash(hash)) {
            sendUploadResult(player, hash, false, "invalid hash");
            return;
        }
        if (payload.totalLength() <= 0 || payload.totalLength() > config.maxModelBytes) {
            sendUploadResult(player, hash, false, "model too large");
            return;
        }
        int expectedChunks = (payload.totalLength() + Chunking.CHUNK_SIZE - 1) / Chunking.CHUNK_SIZE;
        if (payload.chunkCount() <= 0 || payload.chunkCount() != expectedChunks) {
            sendUploadResult(player, hash, false, "invalid chunk count");
            return;
        }
        if (store.hasModel(hash)) {
            sendUploadResult(player, hash, true, "already cached");
            return;
        }
        UPLOADS.put(player.getUuid(), new Upload(hash, new Chunking.Assembler(payload.totalLength(), payload.chunkCount()),
                System.currentTimeMillis()));
    }

    public static void onUploadChunk(ServerPlayerEntity player, UploadChunkC2S payload) {
        Upload upload = UPLOADS.get(player.getUuid());
        if (upload == null || !upload.hash.equals(payload.hash())) {
            return;
        }
        upload.assembler.accept(payload.index(), payload.data());
    }

    public static void onUploadEnd(ServerPlayerEntity player, UploadEndC2S payload) {
        Upload upload = UPLOADS.remove(player.getUuid());
        if (upload == null || !upload.hash.equals(payload.hash())) {
            return;
        }
        if (!upload.assembler.isComplete()) {
            sendUploadResult(player, payload.hash(), false, "missing chunks");
            return;
        }
        try {
            byte[] bytes = upload.assembler.finish();
            boolean ok = store.putModel(payload.hash(), bytes);
            sendUploadResult(player, payload.hash(), ok, ok ? "ok" : "rejected");
            if (ok && config.logUploads) {
                PlayerModelMod.LOG.info("Stored player model {} ({} bytes) from {}",
                        payload.hash().substring(0, 8), bytes.length, player.getGameProfile().getName());
            }
            ServerStore.State state = store.state(player.getUuid());
            if (ok && state != null && payload.hash().equals(state.hash())) {
                broadcastState(player.getUuid());
            }
        } catch (Exception e) {
            sendUploadResult(player, payload.hash(), false, "invalid bundle");
        }
    }

    public static void onRequest(ServerPlayerEntity player, RequestModelC2S payload) {
        if (!ready() || !config.enabled) {
            return;
        }
        String hash = payload.hash();
        if (!validHash(hash)) {
            return;
        }
        byte[] bytes = store.model(hash);
        if (bytes == null) {
            if (ServerPlayNetworking.canSend(player, ModelDeniedS2C.ID)) {
                ServerPlayNetworking.send(player, new ModelDeniedS2C(hash, "not available"));
            }
            return;
        }
        List<byte[]> chunks = Chunking.split(bytes);
        if (!ServerPlayNetworking.canSend(player, ModelBeginS2C.ID)) {
            return;
        }
        ServerPlayNetworking.send(player, new ModelBeginS2C(hash, bytes.length, chunks.size()));
        for (int i = 0; i < chunks.size(); i++) {
            ServerPlayNetworking.send(player, new ModelChunkS2C(hash, i, chunks.get(i)));
        }
        ServerPlayNetworking.send(player, new ModelEndS2C(hash));
    }

    private static void sendUploadResult(ServerPlayerEntity player, String hash, boolean ok, String message) {
        if (ServerPlayNetworking.canSend(player, UploadResultS2C.ID)) {
            ServerPlayNetworking.send(player, new UploadResultS2C(hash == null ? "" : hash, ok, message));
        }
    }

    private static boolean ready() {
        return server != null && store != null && config != null;
    }

    private static boolean validHash(String hash) {
        return hash != null && hash.matches("[0-9a-f]{64}");
    }

    private static String sanitizeName(String name) {
        if (name == null) {
            return "";
        }
        String cleaned = name.replaceAll("\\p{Cntrl}", "").trim();
        return cleaned.length() > 32 ? cleaned.substring(0, 32) : cleaned;
    }

    private record Upload(String hash, Chunking.Assembler assembler, long startedAt) {
    }
}
