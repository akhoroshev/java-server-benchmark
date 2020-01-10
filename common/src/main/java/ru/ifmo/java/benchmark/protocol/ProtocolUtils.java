package ru.ifmo.java.benchmark.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class ProtocolUtils {
    public static byte[] intToBytes(int value) {
        ByteBuffer b = ByteBuffer.allocate(4);
        b.putInt(value);
        return b.array();
    }

    public static int bytesToInt(byte[] bytes) {
        ByteBuffer b = ByteBuffer.wrap(bytes);
        return b.getInt();
    }

    public static int read(InputStream inputStream, byte[] buffer) throws IOException {
        int remaining = buffer.length;
        while (remaining > 0) {
            int count = inputStream.read(buffer, buffer.length - remaining, remaining);
            if (count == -1 || count == 0) {
                if (remaining == buffer.length) {
                    return count;
                }
                break;
            }
            remaining -= count;
        }
        return buffer.length - remaining;
    }
}
