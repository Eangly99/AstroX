package dev.naruto.astrox.payload.commands;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public interface Command {
    void execute(Player sender, String[] args, JavaPlugin plugin);

    // Optional: Command description for help
    default String getDescription() {
        return "No description";
    }

    // Optional: Usage syntax
    default String getUsage() {
        return "";
    }

    // Optional: Category
    default String getCategory() {
        return "General";
    }
}
