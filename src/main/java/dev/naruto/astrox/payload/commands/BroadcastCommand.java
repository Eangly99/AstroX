package dev.naruto.astrox.payload.commands;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class BroadcastCommand implements Command {
    @Override
    public void execute(Player sender, String[] args, JavaPlugin plugin) {
        if (args.length == 0) {
            sender.sendMessage("§cUsage: #broadcast <message>");
            return;
        }

        String message = String.join(" ", args);

        // Fancy broadcast formats
        String[] formats = {
                "§8[§6§lBROADCAST§8] §f",
                "§6§l» §f",
                "§8§l[§c!§8§l] §f",
                "§7[§e§lANNOUNCEMENT§7] §f"
        };

        String format = formats[0]; // Default format

        // Check for format prefix in message
        if (message.startsWith("!")) {
            format = formats[2];
            message = message.substring(1).trim();
        } else if (message.startsWith(">")) {
            format = formats[1];
            message = message.substring(1).trim();
        }

        Bukkit.broadcastMessage(format + message);
        sender.sendMessage("§e✓ Broadcasted message");
    }

    @Override
    public String getDescription() {
        return "Broadcast custom message";
    }

    @Override
    public String getUsage() {
        return "<message>";
    }

    @Override
    public String getCategory() {
        return "Server";
    }
}
