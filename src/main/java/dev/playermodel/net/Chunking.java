package dev.playermodel.net;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Splits a model bundle into small payloads and reassembles them. */
public final class Chunking {
    public static final int CHUNK_SIZE = 24 * 1024;
    public static final int MAX_CHUNKS = 512;

    private Chunking() {
    }

    public static List<byte[]> split(byte[] data) {
        List<byte[]> chunks = new ArrayList<>();
        for (int offset = 0; offset < data.length; offset += CHUNK_SIZE) {
            int length = Math.min(CHUNK_SIZE, data.length - offset);
            byte[] chunk = new byte[length];
            System.arraycopy(data, offset, chunk, 0, length);
            chunks.add(chunk);
            if (chunks.size() > MAX_CHUNKS) {
                throw new IllegalArgumentException("model bundle has too many chunks");
            }
        }
        return chunks;
    }

    public static final class Assembler {
        private final byte[][] chunks;
        private final int totalLength;
        private int received;

        public Assembler(int totalLength, int chunkCount) {
            if (totalLength < 0 || totalLength > 8 * 1024 * 1024 || chunkCount <= 0 || chunkCount > MAX_CHUNKS) {
                throw new IllegalArgumentException("invalid transfer size");
            }
            this.totalLength = totalLength;
            this.chunks = new byte[chunkCount][];
        }

        public boolean accept(int index, byte[] data) {
            if (index < 0 || index >= chunks.length || chunks[index] != null) {
                return false;
            }
            chunks[index] = data;
            received++;
            return true;
        }

        public boolean isComplete() {
            return received == chunks.length;
        }

        public int received() {
            return received;
        }

        public int total() {
            return chunks.length;
        }

        public byte[] finish() {
            ByteArrayOutputStream out = new ByteArrayOutputStream(totalLength);
            for (byte[] chunk : chunks) {
                if (chunk == null) {
                    throw new IllegalStateException("missing chunk");
                }
                out.writeBytes(chunk);
            }
            byte[] result = out.toByteArray();
            if (result.length != totalLength) {
                throw new IllegalStateException("size mismatch");
            }
            return result;
        }
    }
}
