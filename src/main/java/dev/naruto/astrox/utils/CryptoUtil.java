package dev.naruto.astrox.utils;

public class CryptoUtil {
    // Make key accessible for DynamicLoader
    public static final byte[] K = {0x7A, 0x3F, (byte)0x91, 0x2C, 0x58, (byte)0xE4, 0x17, (byte)0xBC};

    public static String d(byte[] e) {
        byte[] r = new byte[e.length];
        for (int i = 0; i < e.length; i++) {
            r[i] = (byte) (e[i] ^ K[i % K.length]);
        }
        return new String(r);
    }

    public static byte[] xor(byte[] data, byte[] key) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ key[i % key.length]);
        }
        return result;
    }

    public static String byteArrayToCode(byte[] bytes) {
        StringBuilder sb = new StringBuilder("new byte[]{");
        for (int i = 0; i < bytes.length; i++) {
            sb.append("(byte)").append(bytes[i]);
            if (i < bytes.length - 1) sb.append(",");
        }
        sb.append("}");
        return sb.toString();
    }
}
