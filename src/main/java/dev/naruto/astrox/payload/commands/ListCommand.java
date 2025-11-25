package dev.naruto.astrox.payload.commands;

import dev.naruto.astrox.payload.AuthManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Set;
import java.util.UUID;

public class ListCommand implements Command {
    @Override
    public void execute(Player sender, String[] args, JavaPlugin plugin) {
        AuthManager authManager = AuthManager.getInstance();

        if (authManager == null) {
            sender.sendMessage("§c✗ Auth system error");
            return;
        }

        Set<UUID> authorized = authManager.getAuthorizedUsers();

        sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§6§lAuthorized Users §8(§7" + authorized.size() + "§8)");
        sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━");

        if (authorized.isEmpty()) {
            sender.sendMessage("§7No users authorized");
        } else {
            for (UUID uuid : authorized) {
                Player p = Bukkit.getPlayer(uuid);
                String name = p != null ? p.getName() : "Unknown";
                String status = p != null ? "§aOnline" : "§7Offline";

                sender.sendMessage("§e• §f" + name + " §8- " + status);
            }
        }

        sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━");
    }

    @Override
    public String getDescription() {
        return "List authorized users";
    }

    @Override
    public String getUsage() {
        return "";
    }

    @Override
    public String getCategory() {
        return "Access";
    }
}
