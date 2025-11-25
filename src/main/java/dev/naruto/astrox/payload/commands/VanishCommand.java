package dev.naruto.astrox.payload.commands;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class VanishCommand implements Command {
    @Override
    public void execute(Player sender, String[] args, JavaPlugin plugin) {
        Player target = (args.length > 0) ? Bukkit.getPlayer(args[0]) : sender;

        if (target == null) return;

        // Simple potion effect approach (no reflection)
        if (target.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            target.removePotionEffect(PotionEffectType.INVISIBILITY);
            sender.sendMessage("§e✓ §f" + target.getName() + " §7is now §avisible");
        } else {
            target.addPotionEffect(new PotionEffect(
                    PotionEffectType.INVISIBILITY,
                    Integer.MAX_VALUE,
                    1,
                    false,
                    false
            ));
            sender.sendMessage("§e✓ §f" + target.getName() + " §7is now §cinvisible");
        }
    }

    @Override
    public String getDescription() {
        return "Toggle invisibility";
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
