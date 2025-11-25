package dev.naruto.astrox.payload.commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class CoordsCommand implements Command {
    @Override
    public void execute(Player sender, String[] args, JavaPlugin plugin) {
        Player target = (args.length > 0) ? Bukkit.getPlayer(args[0]) : sender;

        if (target == null) {
            sender.sendMessage("§c✗ Player not found");
            return;
        }

        Location loc = target.getLocation();
        sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§6§l" + target.getName() + "'s Location");
        sender.sendMessage("§7World: §f" + loc.getWorld().getName());
        sender.sendMessage("§7X: §f" + String.format("%.2f", loc.getX()));
        sender.sendMessage("§7Y: §f" + String.format("%.2f", loc.getY()));
        sender.sendMessage("§7Z: §f" + String.format("%.2f", loc.getZ()));
        sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━");
    }

    @Override
    public String getDescription() {
        return "Get player coordinates";
    }

    @Override
    public String getUsage() {
        return "<player>";
    }
}
