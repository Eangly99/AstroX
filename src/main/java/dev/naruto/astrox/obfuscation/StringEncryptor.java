package dev.naruto.astrox.obfuscation;

import dev.naruto.astrox.Config;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * XOR-based string encryption for obfuscating constants
 */
public class StringEncryptor {
    private final byte[] key;

    public StringEncryptor() {
        this.key = Config.XOR_KEY;
    }

    /**
     * Encrypt string using XOR
     */
    public byte[] encrypt(String plaintext) {
        byte[] data = plaintext.getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = new byte[data.length];

        for (int i = 0; i < data.length; i++) {
            encrypted[i] = (byte) (data[i] ^ key[i % key.length]);
        }

        return encrypted;
    }

    /**
     * Decrypt XOR-encrypted bytes
     */
    public static String decrypt(byte[] encrypted, byte[] key) {
        byte[] decrypted = new byte[encrypted.length];

        for (int i = 0; i < encrypted.length; i++) {
            decrypted[i] = (byte) (encrypted[i] ^ key[i % key.length]);
        }

        return new String(decrypted, StandardCharsets.UTF_8);
    }

    /**
     * Generate Java code for decryption method
     * This gets embedded into the payload class
     */
    public static String generateDecryptMethod() {
        return """
            private static String decrypt(byte[] enc, byte[] key) {
                byte[] dec = new byte[enc.length];
                for (int i = 0; i < enc.length; i++) {
                    dec[i] = (byte) (enc[i] ^ key[i % key.length]);
                }
                return new String(dec, java.nio.charset.StandardCharsets.UTF_8);
            }
            """;
    }

    /**
     * Convert byte array to Java code literal
     */
    public static String byteArrayToCode(byte[] bytes) {
        StringBuilder sb = new StringBuilder("new byte[]{");
        for (int i = 0; i < bytes.length; i++) {
            sb.append(bytes[i]);
            if (i < bytes.length - 1) sb.append(",");
        }
        sb.append("}");
        return sb.toString();
    }
}
