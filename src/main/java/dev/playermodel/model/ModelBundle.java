package dev.playermodel.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Versioned binary container for a normalized model plus its PNG texture. */
public final class ModelBundle {
    public static final int MAGIC = 0x504D4D42; // PMMB
    public static final int VERSION = 1;
    public static final int MAX_BUNDLE_BYTES = 8 * 1024 * 1024;
    public static final int MAX_JSON_BYTES = 4 * 1024 * 1024;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public final NormalizedModel model;
    public final byte[] png;
    public final byte[] bytes;
    public final String hash;

    private ModelBundle(NormalizedModel model, byte[] png, byte[] bytes) {
        this.model = model;
        this.png = png;
        this.bytes = bytes;
        this.hash = sha256(bytes);
    }

    public static ModelBundle create(NormalizedModel model, byte[] png) {
        byte[] actualPng = model.texturePng != null ? model.texturePng : png;
        if (actualPng == null) {
            throw new IllegalArgumentException("model has no texture");
        }
        png = actualPng;
        byte[] modelJson = GSON.toJson(model).getBytes(StandardCharsets.UTF_8);
        if (modelJson.length > MAX_JSON_BYTES) {
            throw new IllegalArgumentException("model json too large: " + modelJson.length);
        }
        byte[] metaJson = GSON.toJson(new Meta(model.displayName, model.textureMode, model.pivotMode,
                model.textureWidth, model.textureHeight)).getBytes(StandardCharsets.UTF_8);
        int total = 4 + 4 + 4 + metaJson.length + 4 + modelJson.length + 4 + png.length;
        if (total > MAX_BUNDLE_BYTES) {
            throw new IllegalArgumentException("model bundle too large: " + total);
        }
        byte[] out = new byte[total];
        int p = 0;
        p = putInt(out, p, MAGIC);
        p = putInt(out, p, VERSION);
        p = putInt(out, p, metaJson.length);
        System.arraycopy(metaJson, 0, out, p, metaJson.length);
        p += metaJson.length;
        p = putInt(out, p, modelJson.length);
        System.arraycopy(modelJson, 0, out, p, modelJson.length);
        p += modelJson.length;
        p = putInt(out, p, png.length);
        System.arraycopy(png, 0, out, p, png.length);
        return new ModelBundle(model, png, out);
    }

    public static ModelBundle fromBytes(byte[] data) {
        if (data == null || data.length < 20 || data.length > MAX_BUNDLE_BYTES) {
            throw new IllegalArgumentException("invalid bundle size");
        }
        Cursor c = new Cursor(data);
        if (c.readInt() != MAGIC) {
            throw new IllegalArgumentException("not a PlayerModel bundle");
        }
        int version = c.readInt();
        if (version != VERSION) {
            throw new IllegalArgumentException("unsupported bundle version " + version);
        }
        byte[] metaJson = c.readBytes(c.readInt(), 64 * 1024);
        byte[] modelJson = c.readBytes(c.readInt(), MAX_JSON_BYTES);
        byte[] png = c.readBytes(c.readInt(), MAX_JSON_BYTES);
        c.expectEnd();
        Meta meta = GSON.fromJson(new String(metaJson, StandardCharsets.UTF_8), Meta.class);
        NormalizedModel model = GSON.fromJson(new String(modelJson, StandardCharsets.UTF_8), NormalizedModel.class);
        if (meta != null) {
            model.displayName = meta.displayName;
            model.textureMode = meta.textureMode;
            model.pivotMode = meta.pivotMode;
            model.textureWidth = meta.textureWidth;
            model.textureHeight = meta.textureHeight;
        }
        if (model.bones == null || model.bones.isEmpty()) {
            throw new IllegalArgumentException("bundle has no bones");
        }
        return new ModelBundle(model, png, data.clone());
    }

    public static String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String shortHash() {
        return hash.substring(0, 8);
    }

    private static int putInt(byte[] out, int offset, int value) {
        out[offset] = (byte) (value >>> 24);
        out[offset + 1] = (byte) (value >>> 16);
        out[offset + 2] = (byte) (value >>> 8);
        out[offset + 3] = (byte) value;
        return offset + 4;
    }

    private record Meta(String displayName, String textureMode, String pivotMode, int textureWidth,
                        int textureHeight) {
    }

    private static final class Cursor {
        private final byte[] data;
        private int offset;

        Cursor(byte[] data) {
            this.data = data;
        }

        int readInt() {
            if (offset + 4 > data.length) {
                throw new IllegalArgumentException("truncated bundle");
            }
            int value = ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16)
                    | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
            offset += 4;
            return value;
        }

        byte[] readBytes(int length, int max) {
            if (length < 0 || length > max || offset + length > data.length) {
                throw new IllegalArgumentException("invalid bundle section length " + length);
            }
            byte[] result = new byte[length];
            System.arraycopy(data, offset, result, 0, length);
            offset += length;
            return result;
        }

        void expectEnd() {
            if (offset != data.length) {
                throw new IllegalArgumentException("trailing bundle data");
            }
        }
    }
}
