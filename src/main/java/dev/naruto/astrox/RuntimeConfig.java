package dev.naruto.astrox;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runtime configuration (set via CLI arguments).
 * Overrides compile-time Config values.
 * Fields are volatile for thread-safe hot-reload support.
 */
public class RuntimeConfig {
    // Core settings
    public static volatile String commandPrefix = Config.COMMAND_PREFIX;
    public static volatile List<String> preAuthorizedUUIDs = new ArrayList<>();
    public static volatile boolean debugMode = Config.DEBUG_MODE;

    // Webhook
    public static volatile String webhookUrl = null;

    // Propagation control
    public static volatile boolean enablePropagation = true;

    // C2 Channel
    public static volatile String c2Url = null;
    public static volatile int c2PollIntervalSeconds = 10;
    public static volatile String instanceId = UUID.randomUUID().toString().substring(0, 8);

    // Stealth mode
    public static volatile boolean stealthMode = false;

    // Encryption key (hex-encoded, set at injection time)
    public static volatile String encryptionKeyHex = null;

    // Config file path for hot-reload (set at injection time)
    public static volatile String configFilePath = null;

    // Dry-run mode (CLI only — no output produced)
    public static volatile boolean dryRun = false;

    // Report output path
    public static volatile String reportPath = null;

    // Batch processing
    public static volatile String targetDir = null;
    public static volatile int threadCount = Runtime.getRuntime().availableProcessors();
}
