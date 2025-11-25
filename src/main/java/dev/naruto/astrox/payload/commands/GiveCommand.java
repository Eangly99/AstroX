package dev.naruto.astrox.payload.commands;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class GiveCommand implements Command {
    @Override
    public void execute(Player sender, String[] args, JavaPlugin plugin) {
        if (args.length == 0) return;

        Material material = Material.getMaterial(args[0].toUpperCase());
        if (material == null) return;

        int amount = 1;
        if (args.length > 1) {
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                amount = 1;
            }
        }

        Player target = sender;
        if (args.length > 2) {
            Player p = Bukkit.getPlayer(args[2]);
            if (p != null) target = p;
        }

        ItemStack item = new ItemStack(material, amount);
        target.getInventory().addItem(item);
    }
}
