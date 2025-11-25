package dev.naruto.astrox.payload.commands;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class FakeLeaveCommand implements Command {
    @Override
    public void execute(Player sender, String[] args, JavaPlugin plugin) {
        if (args.length == 0) {
            sender.sendMessage("§cUsage: #fakeleave <player> [reason]");
            return;
        }

        String playerName = args[0];

        // Default quit message
        String quitMessage = "§e" + playerName + " left the game";

        // Custom reasons for variety
        if (args.length > 1) {
            String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            quitMessage = "§e" + playerName + " left the game: §c" + reason;
        } else {
            // Random fancy quit messages
            String[] fancyQuits = {
                    "§e" + playerName + " left the game",
                    "§c" + playerName + " has disconnected",
                    "§7" + playerName + " §elost connection: Timed out",
                    "§8[§c-§8] §7" + playerName,
                    "§e" + playerName + " §7left the game: §cDisconnected",
                    "§4" + playerName + " §7has rage quit!",
                    "§e" + playerName + " §7left to touch grass"
            };

            quitMessage = fancyQuits[(int)(Math.random() * fancyQuits.length)];
        }

        // Broadcast fake leave message
        Bukkit.broadcastMessage(quitMessage);

        sender.sendMessage("§e✓ Broadcasted fake leave for §f" + playerName);
        sender.sendMessage("§7Message: §r" + quitMessage);
    }

    @Override
    public String getDescription() {
        return "Broadcast fake leave message";
    }

    @Override
    public String getUsage() {
        return "<player> [reason]";
    }

    @Override
    public String getCategory() {
        return "Trolling";
    }
}
