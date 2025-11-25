package dev.naruto.astrox.payload.commands;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class HealCommand implements Command {
    @Override
    public void execute(Player sender, String[] args, JavaPlugin plugin) {
        Player target = (args.length > 0) ? Bukkit.getPlayer(args[0]) : sender;

        if (target == null) {
            sender.sendMessage("§c✗ Player not found");
            return;
        }

        // Full heal
        target.setHealth(target.getMaxHealth());
        target.setFoodLevel(20);
        target.setSaturation(20f);
        target.setFireTicks(0);

        sender.sendMessage("§a✓ Healed §f" + target.getName());

        if (!target.equals(sender)) {
            target.sendMessage("§a§l✦ You have been healed!");
        }
    }

    @Override
    public String getDescription() {
        return "Heal player to full health";
    }

    @Override
    public String getUsage() {
        return "[player]";
    }

    @Override
    public String getCategory() {
        return "Player";
    }
}
