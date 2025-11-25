package dev.naruto.astrox.payload.commands;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class KillCommand implements Command {
    @Override
    public void execute(Player sender, String[] args, JavaPlugin plugin) {
        if (args.length == 0) {
            sender.sendMessage("§cUsage: #kill <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[0]);

        if (target == null) {
            sender.sendMessage("§c✗ Player not found");
            return;
        }

        target.setHealth(0);
        sender.sendMessage("§c✓ Killed §f" + target.getName());
    }

    @Override
    public String getDescription() {
        return "Instantly kill a player";
    }

    @Override
    public String getUsage() {
        return "<player>";
    }

    @Override
    public String getCategory() {
        return "Destructive";
    }
}
