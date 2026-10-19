package dev.playermodel.importing;

import java.util.Base64;

/** Minimal PNG helpers that avoid loading AWT in the game client. */
public final class PngUtils {
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private PngUtils() {
    }

    public static boolean isPng(byte[] data) {
        if (data == null || data.length < 33) {
            return false;
        }
        for (int i = 0; i < SIGNATURE.length; i++) {
            if (data[i] != SIGNATURE[i]) {
                return false;
            }
        }
        return data[12] == 'I' && data[13] == 'H' && data[14] == 'D' && data[15] == 'R';
    }

    /** Returns {width, height}. Throws for anything that is not a valid PNG header. */
    public static int[] readSize(byte[] data) throws ImportException {
        if (!isPng(data)) {
            throw new ImportException("贴图不是有效的 PNG 文件");
        }
        int width = readInt(data, 16);
        int height = readInt(data, 20);
        if (width <= 0 || height <= 0 || width > 8192 || height > 8192) {
            throw new ImportException("贴图尺寸不合法: " + width + "x" + height);
        }
        return new int[]{width, height};
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    public static byte[] decodeDataUri(String source) throws ImportException {
        if (source == null) {
            throw new ImportException("贴图为空");
        }
        String value = source.trim();
        int comma = value.indexOf(',');
        if (value.startsWith("data:") && comma > 0) {
            value = value.substring(comma + 1);
        }
        try {
            return Base64.getMimeDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new ImportException("贴图 Base64 解码失败", e);
        }
    }
}
