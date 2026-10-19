package dev.playermodel.net;

import dev.playermodel.server.ServerSync;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;

/** Registers payload codecs and the server-side handlers (also used by the integrated server). */
public final class Networking {
    private Networking() {
    }

    public static void registerPayloads() {
        PayloadTypeRegistry<RegistryByteBuf> c2s = PayloadTypeRegistry.playC2S();
        PayloadTypeRegistry<RegistryByteBuf> s2c = PayloadTypeRegistry.playS2C();

        c2s.register(Payloads.HelloC2S.ID, Payloads.HelloC2S.CODEC);
        c2s.register(Payloads.StateC2S.ID, Payloads.StateC2S.CODEC);
        c2s.register(Payloads.RequestModelC2S.ID, Payloads.RequestModelC2S.CODEC);
        c2s.register(Payloads.UploadBeginC2S.ID, Payloads.UploadBeginC2S.CODEC);
        c2s.register(Payloads.UploadChunkC2S.ID, Payloads.UploadChunkC2S.CODEC);
        c2s.register(Payloads.UploadEndC2S.ID, Payloads.UploadEndC2S.CODEC);

        s2c.register(Payloads.HelloS2C.ID, Payloads.HelloS2C.CODEC);
        s2c.register(Payloads.StateS2C.ID, Payloads.StateS2C.CODEC);
        s2c.register(Payloads.SnapshotS2C.ID, Payloads.SnapshotS2C.CODEC);
        s2c.register(Payloads.NeedModelS2C.ID, Payloads.NeedModelS2C.CODEC);
        s2c.register(Payloads.UploadResultS2C.ID, Payloads.UploadResultS2C.CODEC);
        s2c.register(Payloads.ModelBeginS2C.ID, Payloads.ModelBeginS2C.CODEC);
        s2c.register(Payloads.ModelChunkS2C.ID, Payloads.ModelChunkS2C.CODEC);
        s2c.register(Payloads.ModelEndS2C.ID, Payloads.ModelEndS2C.CODEC);
        s2c.register(Payloads.ModelDeniedS2C.ID, Payloads.ModelDeniedS2C.CODEC);
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(Payloads.HelloC2S.ID,
                (payload, context) -> context.server().execute(() -> ServerSync.onHello(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.StateC2S.ID,
                (payload, context) -> context.server().execute(() -> ServerSync.onState(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.UploadBeginC2S.ID,
                (payload, context) -> context.server().execute(() -> ServerSync.onUploadBegin(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.UploadChunkC2S.ID,
                (payload, context) -> context.server().execute(() -> ServerSync.onUploadChunk(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.UploadEndC2S.ID,
                (payload, context) -> context.server().execute(() -> ServerSync.onUploadEnd(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.RequestModelC2S.ID,
                (payload, context) -> context.server().execute(() -> ServerSync.onRequest(context.player(), payload)));
    }
}
