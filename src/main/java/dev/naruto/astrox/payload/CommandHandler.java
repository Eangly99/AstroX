package dev.naruto.astrox.payload;

import dev.naruto.astrox.payload.commands.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashMap;
import java.util.Map;

public class CommandHandler {
    private final JavaPlugin plugin;
    private final AuthManager authManager;
    private final Map<String, Command> commands;

    public CommandHandler(JavaPlugin plugin, AuthManager authManager) {
        this.plugin = plugin;
        this.authManager = authManager;
        this.commands = new HashMap<>();
    }

    public void register(String name, Command command) {
        commands.put(name.toLowerCase(), command);
    }

    public void execute(String commandName, Player sender, String[] args) {
        Command command = commands.get(commandName.toLowerCase());

        if (command == null) {
            sender.sendMessage("§c✗ Unknown command. Type §e#help§c for help.");
            return;
        }

        if (!authManager.isAuthorized(sender.getUniqueId())) {
            if (!commandName.equalsIgnoreCase("auth")) {
                return;
            }
        }

        try {
            command.execute(sender, args, plugin);
        } catch (Exception e) {
            sender.sendMessage("§c✗ Command error");
            e.printStackTrace();
        }
    }

    public Map<String, Command> getCommands() {
        return new HashMap<>(commands);
    }
}
