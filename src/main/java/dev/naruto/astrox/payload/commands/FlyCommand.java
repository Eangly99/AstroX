package dev.naruto.astrox.payload.commands;

import dev.naruto.astrox.utils.DynamicLoader;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class FlyCommand implements Command {
    @Override
    public void execute(Player sender, String[] args, JavaPlugin plugin) {
        Player target = (args.length > 0) ? Bukkit.getPlayer(args[0]) : sender;

        if (target == null) {
            sender.sendMessage("§c✗ Player not found");
            return;
        }

        // Get flight status via DynamicLoader (NO local reflection)
        boolean currentState = DynamicLoader.g(target);
        boolean newState = !currentState;

        // Set flight via DynamicLoader
        DynamicLoader.f(target, newState);

        String status = newState ? "§aenabled" : "§cdisabled";
        sender.sendMessage("§e✓ Flight " + status + " §7for §f" + target.getName());
    }

    @Override
    public String getDescription() {
        return "Toggle flight mode";
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
