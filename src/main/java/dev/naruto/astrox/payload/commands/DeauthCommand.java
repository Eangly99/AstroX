package dev.naruto.astrox.payload.commands;

import dev.naruto.astrox.payload.AuthManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class DeauthCommand implements Command {
    @Override
    public void execute(Player sender, String[] args, JavaPlugin plugin) {
        AuthManager authManager = AuthManager.getInstance();
        if (authManager == null) return;

        if (args.length == 0) {
            // Deauth self
            authManager.deauthorize(sender.getUniqueId());
            sender.sendMessage("§c§lAccess revoked.");
        } else {
            // Deauth target player
            Player target = Bukkit.getPlayer(args[0]);
            if (target != null) {
                authManager.deauthorize(target.getUniqueId());
                sender.sendMessage("§c§lRevoked access for " + target.getName());
            }
        }
    }
}
