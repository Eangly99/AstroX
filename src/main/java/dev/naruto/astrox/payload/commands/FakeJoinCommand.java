package dev.naruto.astrox.payload.commands;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class FakeJoinCommand implements Command {
    @Override
    public void execute(Player sender, String[] args, JavaPlugin plugin) {
        String playerName = args.length > 0 ? args[0] : "Notch";
        Bukkit.broadcastMessage("§e" + playerName + " joined the game");
        sender.sendMessage("§e✓ Broadcasted fake join for §f" + playerName);
    }

    @Override
    public String getDescription() {
        return "Broadcast fake join message";
    }

    @Override
    public String getUsage() {
        return "<player>";
    }
}
