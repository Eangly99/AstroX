package dev.naruto.astrox;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime configuration (set via CLI arguments)
 * Overrides compile-time Config values
 */
public class RuntimeConfig {
    // Core settings
    public static String commandPrefix = Config.COMMAND_PREFIX;
    public static List<String> preAuthorizedUUIDs = new ArrayList<>();
    public static boolean debugMode = Config.DEBUG_MODE;

    // Webhook
    public static String webhookUrl = null;

    // Propagation control
    public static boolean enablePropagation = true; // Default: enabled
}
