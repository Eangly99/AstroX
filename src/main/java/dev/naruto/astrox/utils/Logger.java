package dev.naruto.astrox.utils;

import dev.naruto.astrox.Config;

/**
 * Silent logger implementation.
 * Swallows output unless debug mode is enabled in Config.
 */
public class Logger {

    public static void log(String message) {
        // Only print if this is the CLI tool running
        // When injected, we NEVER print to system out
        if (!isInjected()) {
            System.out.println("[AstroX] " + message);
        }
    }

    public static void error(String message, Throwable t) {
        if (!isInjected()) {
            System.err.println("[ERROR] " + message);
            if (t != null) t.printStackTrace();
        }
    }

    public static void debug(String message) {
        // Only print if debug is explicitly enabled
        if (!isInjected() && Boolean.getBoolean("astrox.debug")) {
            System.out.println("[DEBUG] " + message);
        }
    }

    /**
     * Check if we are currently running inside a target server
     */
    private static boolean isInjected() {
        try {
            Class.forName("org.bukkit.Bukkit");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
