package dev.naruto.astrox.obfuscation;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption engine for payload bytecode and string obfuscation.
 * Replaces the legacy XOR-based encryption with authenticated encryption.
 *
 * <p>Each encryption operation generates a unique 96-bit IV prepended to the ciphertext.
 * Wire format: [12-byte IV][ciphertext+GCM-tag]</p>
 */
public class StringEncryptor {

    private static final String ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final int KEY_BITS = 256;

    private final SecretKey key;
    private final SecureRandom secureRandom;

    /**
     * Create an encryptor with a randomly generated 256-bit key.
     */
    public StringEncryptor() {
        this(generateKey());
    }

    /**
     * Create an encryptor with a specific key.
     */
    public StringEncryptor(SecretKey key) {
        this.key = key;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Create an encryptor from a hex-encoded key string.
     */
    public StringEncryptor(String hexKey) {
        this(new SecretKeySpec(hexToBytes(hexKey), ALGORITHM));
    }

    /**
     * Generate a random AES-256 key.
     */
    public static SecretKey generateKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(KEY_BITS, new SecureRandom());
            return keyGen.generateKey();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate AES-256 key", e);
        }
    }

    /**
     * Encrypt plaintext string. Returns IV + ciphertext as byte array.
     */
    public byte[] encrypt(String plaintext) {
        return encrypt(plaintext.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Encrypt raw bytes. Returns IV + ciphertext as byte array.
     */
    public byte[] encrypt(byte[] data) {
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));

            byte[] ciphertext = cipher.doFinal(data);

            // Prepend IV to ciphertext
            ByteBuffer buffer = ByteBuffer.allocate(IV_BYTES + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return buffer.array();
        } catch (Exception e) {
            throw new RuntimeException("AES-256-GCM encryption failed", e);
        }
    }

    /**
     * Decrypt ciphertext (IV + ciphertext format) back to plaintext string.
     */
    public String decryptString(byte[] encrypted) {
        return new String(decrypt(encrypted), StandardCharsets.UTF_8);
    }

    /**
     * Decrypt ciphertext (IV + ciphertext format) back to raw bytes.
     */
    public byte[] decrypt(byte[] encrypted) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encrypted);

            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);

            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));

            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new RuntimeException("AES-256-GCM decryption failed", e);
        }
    }

    /**
     * Static decrypt method for use in payload bootstrap.
     */
    public static byte[] decrypt(byte[] encrypted, byte[] keyBytes) {
        try {
            SecretKey secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
            ByteBuffer buffer = ByteBuffer.wrap(encrypted);

            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);

            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));

            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new RuntimeException("AES-256-GCM decryption failed", e);
        }
    }

    /**
     * Get the encryption key as a hex string for embedding.
     */
    public String getKeyHex() {
        return bytesToHex(key.getEncoded());
    }

    /**
     * Get the raw key bytes.
     */
    public byte[] getKeyBytes() {
        return key.getEncoded();
    }

    /**
     * Get the SecretKey object.
     */
    public SecretKey getKey() {
        return key;
    }

    /**
     * Encrypt data and return as Base64 string (for embedding in source).
     */
    public String encryptToBase64(byte[] data) {
        return Base64.getEncoder().encodeToString(encrypt(data));
    }

    /**
     * Decrypt Base64-encoded ciphertext.
     */
    public byte[] decryptFromBase64(String base64) {
        return decrypt(Base64.getDecoder().decode(base64));
    }

    /**
     * Generate Java source code for a byte array literal.
     */
    public static String byteArrayToCode(byte[] bytes) {
        StringBuilder sb = new StringBuilder("new byte[]{");
        for (int i = 0; i < bytes.length; i++) {
            sb.append("(byte)").append(bytes[i]);
            if (i < bytes.length - 1) sb.append(",");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Generate Java source code for the decryption method (embedded in payload).
     */
    public static String generateDecryptMethod() {
        return """
            private static byte[] aesDecrypt(byte[] enc, byte[] keyBytes) {
                try {
                    javax.crypto.spec.SecretKeySpec k = new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
                    java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(enc);
                    byte[] iv = new byte[12];
                    buf.get(iv);
                    byte[] ct = new byte[buf.remaining()];
                    buf.get(ct);
                    javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
                    c.init(javax.crypto.Cipher.DECRYPT_MODE, k, new javax.crypto.spec.GCMParameterSpec(128, iv));
                    return c.doFinal(ct);
                } catch (Exception e) { throw new RuntimeException(e); }
            }
            """;
    }

    // ==================== Legacy XOR compatibility ====================

    /**
     * Legacy XOR encryption (kept for backward compatibility with existing payloads).
     */
    public static byte[] xorEncrypt(byte[] data, byte[] xorKey) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ xorKey[i % xorKey.length]);
        }
        return result;
    }

    /**
     * Legacy XOR decryption.
     */
    public static String xorDecrypt(byte[] encrypted, byte[] xorKey) {
        byte[] decrypted = new byte[encrypted.length];
        for (int i = 0; i < encrypted.length; i++) {
            decrypted[i] = (byte) (encrypted[i] ^ xorKey[i % xorKey.length]);
        }
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    // ==================== Hex utilities ====================

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
