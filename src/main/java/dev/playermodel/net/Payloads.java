package dev.playermodel.net;

import dev.playermodel.PlayerModelMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** All custom payloads used by the client/server sync protocol. */
public final class Payloads {
    public static final int PROTOCOL = 1;

    private Payloads() {
    }

    private static <T extends CustomPayload> CustomPayload.Id<T> id(String path) {
        return new CustomPayload.Id<>(Identifier.of(PlayerModelMod.MOD_ID, path));
    }

    public record HelloC2S(int protocol) implements CustomPayload {
        public static final Id<HelloC2S> ID = id("hello");
        public static final PacketCodec<RegistryByteBuf, HelloC2S> CODEC =
                PacketCodec.of(HelloC2S::write, HelloC2S::new);

        public HelloC2S(RegistryByteBuf buf) {
            this(buf.readVarInt());
        }

        void write(RegistryByteBuf buf) {
            buf.writeVarInt(protocol);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record HelloS2C(int protocol, boolean enabled, int maxModelBytes, int chunkSize) implements CustomPayload {
        public static final Id<HelloS2C> ID = id("hello_s2c");
        public static final PacketCodec<RegistryByteBuf, HelloS2C> CODEC =
                PacketCodec.of(HelloS2C::write, HelloS2C::new);

        public HelloS2C(RegistryByteBuf buf) {
            this(buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt());
        }

        void write(RegistryByteBuf buf) {
            buf.writeVarInt(protocol);
            buf.writeBoolean(enabled);
            buf.writeVarInt(maxModelBytes);
            buf.writeVarInt(chunkSize);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record StateC2S(boolean enabled, String hash, String name) implements CustomPayload {
        public static final Id<StateC2S> ID = id("state");
        public static final PacketCodec<RegistryByteBuf, StateC2S> CODEC =
                PacketCodec.of(StateC2S::write, StateC2S::new);

        public StateC2S(RegistryByteBuf buf) {
            this(buf.readBoolean(), buf.readString(64), buf.readString(64));
        }

        void write(RegistryByteBuf buf) {
            buf.writeBoolean(enabled);
            buf.writeString(hash == null ? "" : hash, 64);
            buf.writeString(name == null ? "" : name, 64);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record StateS2C(UUID uuid, boolean enabled, String hash, String name) implements CustomPayload {
        public static final Id<StateS2C> ID = id("state_s2c");
        public static final PacketCodec<RegistryByteBuf, StateS2C> CODEC =
                PacketCodec.of(StateS2C::write, StateS2C::new);

        public StateS2C(RegistryByteBuf buf) {
            this(buf.readUuid(), buf.readBoolean(), buf.readString(64), buf.readString(64));
        }

        void write(RegistryByteBuf buf) {
            buf.writeUuid(uuid);
            buf.writeBoolean(enabled);
            buf.writeString(hash == null ? "" : hash, 64);
            buf.writeString(name == null ? "" : name, 64);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record SnapshotS2C(List<Entry> entries) implements CustomPayload {
        public static final Id<SnapshotS2C> ID = id("snapshot");
        public static final PacketCodec<RegistryByteBuf, SnapshotS2C> CODEC =
                PacketCodec.of(SnapshotS2C::write, SnapshotS2C::new);

        public SnapshotS2C(RegistryByteBuf buf) {
            this(readEntries(buf));
        }

        void write(RegistryByteBuf buf) {
            buf.writeVarInt(entries.size());
            for (Entry entry : entries) {
                buf.writeUuid(entry.uuid());
                buf.writeBoolean(entry.enabled());
                buf.writeString(entry.hash() == null ? "" : entry.hash(), 64);
                buf.writeString(entry.name() == null ? "" : entry.name(), 64);
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public record Entry(UUID uuid, boolean enabled, String hash, String name) {
        }
    }

    public record NeedModelS2C(String hash) implements CustomPayload {
        public static final Id<NeedModelS2C> ID = id("need_model");
        public static final PacketCodec<RegistryByteBuf, NeedModelS2C> CODEC =
                PacketCodec.of(NeedModelS2C::write, NeedModelS2C::new);

        public NeedModelS2C(RegistryByteBuf buf) {
            this(buf.readString(64));
        }

        void write(RegistryByteBuf buf) {
            buf.writeString(hash, 64);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record RequestModelC2S(String hash) implements CustomPayload {
        public static final Id<RequestModelC2S> ID = id("request_model");
        public static final PacketCodec<RegistryByteBuf, RequestModelC2S> CODEC =
                PacketCodec.of(RequestModelC2S::write, RequestModelC2S::new);

        public RequestModelC2S(RegistryByteBuf buf) {
            this(buf.readString(64));
        }

        void write(RegistryByteBuf buf) {
            buf.writeString(hash, 64);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record UploadBeginC2S(String hash, int totalLength, int chunkCount) implements CustomPayload {
        public static final Id<UploadBeginC2S> ID = id("upload_begin");
        public static final PacketCodec<RegistryByteBuf, UploadBeginC2S> CODEC =
                PacketCodec.of(UploadBeginC2S::write, UploadBeginC2S::new);

        public UploadBeginC2S(RegistryByteBuf buf) {
            this(buf.readString(64), buf.readVarInt(), buf.readVarInt());
        }

        void write(RegistryByteBuf buf) {
            buf.writeString(hash, 64);
            buf.writeVarInt(totalLength);
            buf.writeVarInt(chunkCount);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record UploadChunkC2S(String hash, int index, byte[] data) implements CustomPayload {
        public static final Id<UploadChunkC2S> ID = id("upload_chunk");
        public static final PacketCodec<RegistryByteBuf, UploadChunkC2S> CODEC =
                PacketCodec.of(UploadChunkC2S::write, UploadChunkC2S::new);

        public UploadChunkC2S(RegistryByteBuf buf) {
            this(buf.readString(64), buf.readVarInt(), buf.readByteArray(64 * 1024));
        }

        void write(RegistryByteBuf buf) {
            buf.writeString(hash, 64);
            buf.writeVarInt(index);
            buf.writeByteArray(data);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record UploadEndC2S(String hash) implements CustomPayload {
        public static final Id<UploadEndC2S> ID = id("upload_end");
        public static final PacketCodec<RegistryByteBuf, UploadEndC2S> CODEC =
                PacketCodec.of(UploadEndC2S::write, UploadEndC2S::new);

        public UploadEndC2S(RegistryByteBuf buf) {
            this(buf.readString(64));
        }

        void write(RegistryByteBuf buf) {
            buf.writeString(hash, 64);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record UploadResultS2C(String hash, boolean ok, String message) implements CustomPayload {
        public static final Id<UploadResultS2C> ID = id("upload_result");
        public static final PacketCodec<RegistryByteBuf, UploadResultS2C> CODEC =
                PacketCodec.of(UploadResultS2C::write, UploadResultS2C::new);

        public UploadResultS2C(RegistryByteBuf buf) {
            this(buf.readString(64), buf.readBoolean(), buf.readString(128));
        }

        void write(RegistryByteBuf buf) {
            buf.writeString(hash, 64);
            buf.writeBoolean(ok);
            buf.writeString(message == null ? "" : message, 128);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ModelBeginS2C(String hash, int totalLength, int chunkCount) implements CustomPayload {
        public static final Id<ModelBeginS2C> ID = id("model_begin");
        public static final PacketCodec<RegistryByteBuf, ModelBeginS2C> CODEC =
                PacketCodec.of(ModelBeginS2C::write, ModelBeginS2C::new);

        public ModelBeginS2C(RegistryByteBuf buf) {
            this(buf.readString(64), buf.readVarInt(), buf.readVarInt());
        }

        void write(RegistryByteBuf buf) {
            buf.writeString(hash, 64);
            buf.writeVarInt(totalLength);
            buf.writeVarInt(chunkCount);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ModelChunkS2C(String hash, int index, byte[] data) implements CustomPayload {
        public static final Id<ModelChunkS2C> ID = id("model_chunk");
        public static final PacketCodec<RegistryByteBuf, ModelChunkS2C> CODEC =
                PacketCodec.of(ModelChunkS2C::write, ModelChunkS2C::new);

        public ModelChunkS2C(RegistryByteBuf buf) {
            this(buf.readString(64), buf.readVarInt(), buf.readByteArray(64 * 1024));
        }

        void write(RegistryByteBuf buf) {
            buf.writeString(hash, 64);
            buf.writeVarInt(index);
            buf.writeByteArray(data);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ModelEndS2C(String hash) implements CustomPayload {
        public static final Id<ModelEndS2C> ID = id("model_end");
        public static final PacketCodec<RegistryByteBuf, ModelEndS2C> CODEC =
                PacketCodec.of(ModelEndS2C::write, ModelEndS2C::new);

        public ModelEndS2C(RegistryByteBuf buf) {
            this(buf.readString(64));
        }

        void write(RegistryByteBuf buf) {
            buf.writeString(hash, 64);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ModelDeniedS2C(String hash, String reason) implements CustomPayload {
        public static final Id<ModelDeniedS2C> ID = id("model_denied");
        public static final PacketCodec<RegistryByteBuf, ModelDeniedS2C> CODEC =
                PacketCodec.of(ModelDeniedS2C::write, ModelDeniedS2C::new);

        public ModelDeniedS2C(RegistryByteBuf buf) {
            this(buf.readString(64), buf.readString(128));
        }

        void write(RegistryByteBuf buf) {
            buf.writeString(hash, 64);
            buf.writeString(reason == null ? "" : reason, 128);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private static List<SnapshotS2C.Entry> readEntries(RegistryByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > 1024) {
            throw new IllegalArgumentException("bad snapshot size");
        }
        List<SnapshotS2C.Entry> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(new SnapshotS2C.Entry(buf.readUuid(), buf.readBoolean(),
                    buf.readString(64), buf.readString(64)));
        }
        return result;
    }
}
