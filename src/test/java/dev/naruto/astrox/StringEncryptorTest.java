package dev.naruto.astrox;

import dev.naruto.astrox.obfuscation.StringEncryptor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for AES-256-GCM encryption round-trip correctness.
 */
class StringEncryptorTest {

    private StringEncryptor encryptor;

    @BeforeEach
    void setUp() {
        encryptor = new StringEncryptor();
    }

    @Test
    @DisplayName("Encrypt/decrypt round-trip should preserve plaintext")
    void testEncryptDecryptRoundTrip() {
        String original = "Hello AstroX Security Framework!";
        byte[] encrypted = encryptor.encrypt(original);
        String decrypted = encryptor.decryptString(encrypted);
        assertEquals(original, decrypted);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "a",
            "Hello World",
            "Unicode: 日本語テスト αβγδ ñ",
            "Special chars: !@#$%^&*()_+-=[]{}|;':\",./<>?",
    })
    @DisplayName("Round-trip with various string types")
    void testEncryptDecryptVariousStrings(String original) {
        byte[] encrypted = encryptor.encrypt(original);
        String decrypted = encryptor.decryptString(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    @DisplayName("Round-trip with long string (10KB)")
    void testEncryptDecryptLongString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append((char) ('A' + (i % 26)));
        }
        String original = sb.toString();
        byte[] encrypted = encryptor.encrypt(original);
        String decrypted = encryptor.decryptString(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    @DisplayName("Encrypt/decrypt with different keys should produce different ciphertext")
    void testDifferentKeysProduceDifferentCiphertext() {
        StringEncryptor enc1 = new StringEncryptor();
        StringEncryptor enc2 = new StringEncryptor();

        String plaintext = "test data";
        byte[] ct1 = enc1.encrypt(plaintext);
        byte[] ct2 = enc2.encrypt(plaintext);

        // Ciphertexts should be different (different keys + different IVs)
        assertFalse(java.util.Arrays.equals(ct1, ct2),
                "Same plaintext encrypted with different keys should produce different ciphertext");
    }

    @Test
    @DisplayName("Encrypted data should differ from plaintext bytes")
    void testEncryptProducesDifferentOutput() {
        String plaintext = "test plaintext data for encryption";
        byte[] original = plaintext.getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = encryptor.encrypt(plaintext);

        // Encrypted should be longer (IV + ciphertext + GCM tag)
        assertTrue(encrypted.length > original.length,
                "Encrypted data should be longer than plaintext");

        // Content should differ
        assertFalse(java.util.Arrays.equals(original, 0, original.length,
                        encrypted, 12, 12 + original.length),
                "Encrypted content should differ from plaintext");
    }

    @Test
    @DisplayName("Same plaintext encrypted twice should produce different ciphertext (unique IV)")
    void testUniqueIVPerEncryption() {
        String plaintext = "same plaintext";
        byte[] ct1 = encryptor.encrypt(plaintext);
        byte[] ct2 = encryptor.encrypt(plaintext);

        assertFalse(java.util.Arrays.equals(ct1, ct2),
                "Same plaintext encrypted twice should produce different ciphertext due to unique IV");
    }

    @Test
    @DisplayName("Static decrypt method should work with raw key bytes")
    void testStaticDecrypt() {
        SecretKey key = StringEncryptor.generateKey();
        StringEncryptor enc = new StringEncryptor(key);

        String plaintext = "static decrypt test";
        byte[] encrypted = enc.encrypt(plaintext);

        byte[] decrypted = StringEncryptor.decrypt(encrypted, key.getEncoded());
        assertEquals(plaintext, new String(decrypted, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Hex key round-trip should work")
    void testHexKeyRoundTrip() {
        StringEncryptor original = new StringEncryptor();
        String keyHex = original.getKeyHex();

        StringEncryptor restored = new StringEncryptor(keyHex);

        String plaintext = "hex key test";
        byte[] encrypted = original.encrypt(plaintext);
        String decrypted = restored.decryptString(encrypted);

        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Base64 encrypt/decrypt round-trip")
    void testBase64RoundTrip() {
        byte[] data = "base64 test data".getBytes(StandardCharsets.UTF_8);
        String base64 = encryptor.encryptToBase64(data);
        byte[] decrypted = encryptor.decryptFromBase64(base64);

        assertArrayEquals(data, decrypted);
    }

    @Test
    @DisplayName("Decrypt with wrong key should fail")
    void testDecryptWithWrongKey() {
        StringEncryptor enc1 = new StringEncryptor();
        StringEncryptor enc2 = new StringEncryptor();

        byte[] encrypted = enc1.encrypt("secret");

        assertThrows(RuntimeException.class, () -> enc2.decryptString(encrypted),
                "Decrypting with wrong key should throw");
    }

    @Test
    @DisplayName("Raw byte array encryption round-trip")
    void testRawByteEncryption() {
        byte[] original = new byte[256];
        new SecureRandom().nextBytes(original);

        byte[] encrypted = encryptor.encrypt(original);
        byte[] decrypted = encryptor.decrypt(encrypted);

        assertArrayEquals(original, decrypted);
    }

    // ==================== Legacy XOR tests ====================

    @Test
    @DisplayName("Legacy XOR encrypt/decrypt round-trip")
    void testXorRoundTrip() {
        byte[] key = {0x7A, 0x3F, (byte) 0x91, 0x2C};
        String plaintext = "XOR test";

        byte[] encrypted = StringEncryptor.xorEncrypt(
                plaintext.getBytes(StandardCharsets.UTF_8), key);
        String decrypted = StringEncryptor.xorDecrypt(encrypted, key);

        assertEquals(plaintext, decrypted);
    }
}
