package dev.naruto.astrox;

import org.objectweb.asm.*;

import java.io.*;
import java.util.jar.*;
import java.util.zip.ZipEntry;

/**
 * Test utilities for building dummy plugin JARs programmatically.
 */
public class TestUtils {

    /**
     * Create a minimal valid Bukkit plugin JAR with plugin.yml and a main class.
     *
     * @param outputFile where to write the JAR
     * @param pluginName the plugin name
     * @param mainClass  fully qualified main class name
     */
    public static void createDummyPluginJar(File outputFile, String pluginName,
                                            String mainClass, String version) throws Exception {
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(outputFile))) {

            // 1. plugin.yml
            String pluginYml = String.format("""
                    name: %s
                    version: %s
                    main: %s
                    api-version: '1.21'
                    """, pluginName, version, mainClass);

            jos.putNextEntry(new ZipEntry("plugin.yml"));
            jos.write(pluginYml.getBytes());
            jos.closeEntry();

            // 2. Main class bytecode (extends JavaPlugin with empty onEnable)
            String internalName = mainClass.replace('.', '/');
            byte[] classBytes = generateJavaPluginSubclass(internalName);

            jos.putNextEntry(new ZipEntry(internalName + ".class"));
            jos.write(classBytes);
            jos.closeEntry();
        }
    }

    /**
     * Generate bytecode for a minimal JavaPlugin subclass using ASM.
     *
     * <pre>
     * public class MainClass extends org.bukkit.plugin.java.JavaPlugin {
     *     public void onEnable() {
     *         // empty
     *     }
     * }
     * </pre>
     */
    public static byte[] generateJavaPluginSubclass(String internalName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                internalName, null,
                "org/bukkit/plugin/java/JavaPlugin", null);

        // Default constructor: public MainClass() { super(); }
        MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "org/bukkit/plugin/java/JavaPlugin", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();

        // onEnable(): public void onEnable() { }
        MethodVisitor onEnable = cw.visitMethod(Opcodes.ACC_PUBLIC, "onEnable", "()V", null, null);
        onEnable.visitCode();
        onEnable.visitInsn(Opcodes.RETURN);
        onEnable.visitMaxs(0, 1);
        onEnable.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Create a non-plugin JAR (no plugin.yml) for negative testing.
     */
    public static void createNonPluginJar(File outputFile) throws Exception {
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(outputFile))) {
            jos.putNextEntry(new ZipEntry("com/example/SomeClass.class"));
            // Write a minimal valid class file
            ClassWriter cw = new ClassWriter(0);
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/SomeClass", null,
                    "java/lang/Object", null);
            cw.visitEnd();
            jos.write(cw.toByteArray());
            jos.closeEntry();
        }
    }

    /**
     * Create an invalid/corrupt file for negative testing.
     */
    public static void createCorruptFile(File outputFile) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.write(new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05});
        }
    }
}
