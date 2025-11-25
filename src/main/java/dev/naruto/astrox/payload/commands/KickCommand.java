package dev.naruto.astrox.payload.commands;

import dev.naruto.astrox.utils.DynamicLoader;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class KickCommand implements Command {
    @Override
    public void execute(Player sender, String[] args, JavaPlugin plugin) {
        if (args.length == 0) {
            sender.sendMessage("§cUsage: #kick <player> [reason]");
            return;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§c✗ Player not found");
            return;
        }

        String reason = args.length > 1 ?
                String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) :
                "Kicked by an operator";

        String kickMsg = "§c§l⚠ KICKED ⚠\n\n§7Reason: §f" + reason;

        DynamicLoader.c(target, kickMsg); // FIXED
        sender.sendMessage("§e✓ Kicked §f" + target.getName());
    }

    @Override
    public String getDescription() { return "Kick player"; }

    @Override
    public String getUsage() { return "<player> [reason]"; }
}
