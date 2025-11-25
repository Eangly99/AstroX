package dev.naruto.astrox;

import dev.naruto.astrox.utils.CryptoUtil;
import java.io.*;
import java.nio.file.*;

/**
 * Utility to encrypt ReflectionUtil bytecode
 *
 * USAGE:
 * 1. Build project with: mvn compile
 * 2. Run this: java -cp target/classes dev.naruto.astrox.BuildEncryptor
 * 3. Copy output and paste into DynamicLoader.ENCRYPTED_CLASS
 * 4. Delete ReflectionUtil.java
 * 5. Rebuild with: mvn clean package
 */
public class BuildEncryptor {
    public static void main(String[] args) throws Exception {
        // Path to compiled ReflectionUtil.class
        String classPath = "target/classes/dev/naruto/astrox/utils/ReflectionUtil.class";

        File classFile = new File(classPath);
        if (!classFile.exists()) {
            System.err.println("ERROR: ReflectionUtil.class not found!");
            System.err.println("Run 'mvn compile' first to generate the class file.");
            return;
        }

        // Read bytecode
        byte[] classBytes = Files.readAllBytes(Paths.get(classPath));
        System.out.println("Original class size: " + classBytes.length + " bytes");

        // Encrypt using XOR
        byte[] encrypted = CryptoUtil.xor(classBytes, Config.XOR_KEY);

        // Generate Java code
        System.out.println("\n// ========================================");
        System.out.println("// COPY THE ARRAY BELOW INTO DynamicLoader.java");
        System.out.println("// Replace the ENCRYPTED_CLASS field");
        System.out.println("// ========================================\n");

        System.out.println("private static final byte[] ENCRYPTED_CLASS = {");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < encrypted.length; i++) {
            sb.append("(byte)").append(encrypted[i]);

            if (i < encrypted.length - 1) {
                sb.append(",");
            }

            // New line every 10 bytes for readability
            if ((i + 1) % 10 == 0 || i == encrypted.length - 1) {
                System.out.println("    " + sb.toString());
                sb = new StringBuilder();
            }
        }

        System.out.println("};");
        System.out.println("\n// Encrypted size: " + encrypted.length + " bytes");
        System.out.println("// XOR Key: " + bytesToHex(Config.XOR_KEY));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("0x%02X ", b));
        }
        return sb.toString();
    }
}
