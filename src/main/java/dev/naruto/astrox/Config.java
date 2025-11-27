package dev.naruto.astrox;

import java.security.SecureRandom;

public class Config {
    // Core settings
    public static final String COMMAND_PREFIX = "#";
    public static final String MASTER_KEY = "astrox_master_2025";
    public static final String PAYLOAD_PACKAGE_SUFFIX = "internal.util";

    // Obfuscation
    public static final boolean ENABLE_OBFUSCATION = true;
    public static final int OBFUSCATION_LEVEL = 8;
    public static final boolean ENCRYPT_STRINGS = true;
    public static final byte[] XOR_KEY = generateRandomKey();

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

    private static byte[] generateRandomKey() {
        byte[] key = new byte[16];
        new SecureRandom().nextBytes(key);
        return key;
    }

    public static String getKeyHex() {
        StringBuilder sb = new StringBuilder();
        for (byte b : XOR_KEY) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}
