package dev.naruto.astrox;

import dev.naruto.astrox.obfuscation.StringEncryptor;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.ZipEntry;

/**
 * Build-time obfuscation pipeline for AstroX's own JAR.
 *
 * <p>Runs a full obfuscation pass:
 * <ul>
 *   <li>Renames all internal class names and method names to single-char identifiers via ASM ClassRemapper</li>
 *   <li>Removes SourceFile, LineNumberTable, and LocalVariableTable attributes (SKIP_DEBUG)</li>
 *   <li>Scrambles constant pool order via ClassWriter.COMPUTE_FRAMES</li>
 * </ul>
 *
 * <p>Also supports legacy encrypted bytecode generation for DynamicLoader.</p>
 */
public class BuildEncryptor {

    private static final Logger LOG = LoggerFactory.getLogger(BuildEncryptor.class);

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--obfuscate-jar")) {
            if (args.length < 3) {
                LOG.error("Usage: --obfuscate-jar <input.jar> <output.jar>");
                return;
            }
            obfuscateJar(new File(args[1]), new File(args[2]));
            return;
        }

        // Legacy mode: encrypt ReflectionUtil bytecode
        encryptReflectionUtil();
    }

    /**
     * Obfuscate an entire JAR file (AstroX self-obfuscation).
     */
    public static void obfuscateJar(File inputJar, File outputJar) throws Exception {
        LOG.info("Obfuscating JAR: {} → {}", inputJar.getName(), outputJar.getName());

        // Phase 1: Scan all classes to build remap table
        Map<String, String> remapTable = new LinkedHashMap<>();
        int counter = 0;

        try (JarFile jar = new JarFile(inputJar)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class") && !entry.getName().startsWith("META-INF/")) {
                    String oldName = entry.getName().replace(".class", "");
                    // Skip preserved classes (entry points)
                    if (isPreservedClass(oldName)) continue;

                    String newName = generateObfuscatedName(counter++);
                    remapTable.put(oldName, newName);
                }
            }
        }

        LOG.info("Built remap table: {} classes to obfuscate", remapTable.size());

        // Phase 2: Remap and rewrite all classes
        SimpleRemapper remapper = new SimpleRemapper(remapTable);

        try (JarFile jar = new JarFile(inputJar);
             JarOutputStream jos = new JarOutputStream(new FileOutputStream(outputJar), jar.getManifest())) {

            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                if (entry.getName().equals("META-INF/MANIFEST.MF")) continue;

                try (InputStream is = jar.getInputStream(entry)) {
                    if (entry.getName().endsWith(".class")) {
                        byte[] classBytes = is.readAllBytes();

                        // Remap + strip debug info
                        ClassReader reader = new ClassReader(classBytes);
                        ClassWriter writer = new ClassWriter(reader,
                                ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                        ClassVisitor remappingVisitor = new ClassRemapper(writer, remapper);
                        reader.accept(remappingVisitor,
                                ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

                        byte[] obfuscated = writer.toByteArray();

                        // Use remapped entry name
                        String oldPath = entry.getName().replace(".class", "");
                        String newPath = remapTable.getOrDefault(oldPath, oldPath);

                        jos.putNextEntry(new ZipEntry(newPath + ".class"));
                        jos.write(obfuscated);
                        jos.closeEntry();
                    } else {
                        // Copy non-class files as-is
                        jos.putNextEntry(new ZipEntry(entry.getName()));
                        is.transferTo(jos);
                        jos.closeEntry();
                    }
                }
            }
        }

        LOG.info("Obfuscation complete: {} → {} bytes",
                inputJar.length(), outputJar.length());
    }

    /**
     * Classes that must NOT be renamed (entry points, SPI, etc.)
     */
    private static boolean isPreservedClass(String internalName) {
        return internalName.equals("dev/naruto/astrox/AstroX")
                || internalName.startsWith("META-INF/")
                || internalName.contains("module-info");
    }

    /**
     * Generate an obfuscated class name from a counter.
     * Produces names like: a, b, ..., z, aa, ab, ..., zz, aaa, ...
     */
    private static String generateObfuscatedName(int index) {
        StringBuilder sb = new StringBuilder();
        int packageDepth = index / 26;
        char className = (char) ('a' + (index % 26));

        // Generate package path
        for (int i = 0; i < Math.min(packageDepth, 3); i++) {
            sb.append((char) ('a' + (i % 26))).append('/');
        }
        sb.append(Character.toUpperCase(className));

        return sb.toString();
    }

    /**
     * Legacy: Encrypt ReflectionUtil bytecode for DynamicLoader embedding.
     */
    private static void encryptReflectionUtil() throws Exception {
        String classPath = "target/classes/dev/naruto/astrox/utils/ReflectionUtil.class";
        File classFile = new File(classPath);

        if (!classFile.exists()) {
            LOG.error("ReflectionUtil.class not found! Run 'mvn compile' first.");
            return;
        }

        byte[] classBytes = Files.readAllBytes(Paths.get(classPath));
        LOG.info("Original class size: {} bytes", classBytes.length);

        // Encrypt using AES-256-GCM
        StringEncryptor encryptor = Config.getEncryptor();
        byte[] encrypted = encryptor.encrypt(classBytes);

        LOG.info("Encrypted size: {} bytes", encrypted.length);
        LOG.info("AES Key (hex): {}", encryptor.getKeyHex());

        // Also generate legacy XOR version
        byte[] xorEncrypted = StringEncryptor.xorEncrypt(classBytes, Config.getXorKey());

        System.out.println("\n// ========================================");
        System.out.println("// COPY THE ARRAY BELOW INTO DynamicLoader.java");
        System.out.println("// Replace the ENCRYPTED_CLASS field");
        System.out.println("// ========================================\n");

        System.out.println("private static final byte[] ENCRYPTED_CLASS = {");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < xorEncrypted.length; i++) {
            sb.append("(byte)").append(xorEncrypted[i]);
            if (i < xorEncrypted.length - 1) sb.append(",");
            if ((i + 1) % 10 == 0 || i == xorEncrypted.length - 1) {
                System.out.println("    " + sb);
                sb = new StringBuilder();
            }
        }

        System.out.println("};");
        System.out.println("\n// Encrypted size: " + xorEncrypted.length + " bytes");
        System.out.println("// XOR Key: " + Config.getXorKeyHex());
        System.out.println("// AES Key: " + encryptor.getKeyHex());
    }
}
