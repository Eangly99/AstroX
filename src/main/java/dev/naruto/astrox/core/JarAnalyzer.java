package dev.naruto.astrox.core;

import org.yaml.snakeyaml.Yaml;
import java.io.*;
import java.util.Map;
import java.util.jar.*;

public class JarAnalyzer {
    private final File jarFile;
    private String pluginName;
    private String mainClass;
    private String version;
    private int javaVersion;
    private String basePackage; // NEW

    public JarAnalyzer(File jarFile) {
        this.jarFile = jarFile;
    }

    public void analyze() throws IOException {
        try (JarFile jar = new JarFile(jarFile)) {
            JarEntry pluginYml = jar.getJarEntry("plugin.yml");
            if (pluginYml == null) {
                pluginYml = jar.getJarEntry("paper-plugin.yml");
                if (pluginYml == null) {
                    pluginYml = jar.getJarEntry("bungee.yml");
                }
            }

            if (pluginYml == null) {
                throw new IOException("No plugin.yml found - not a valid plugin!");
            }

            parsePluginYml(jar.getInputStream(pluginYml));
            extractBasePackage(); // NEW
            detectJavaVersion(jar);
        }
    }

    private void parsePluginYml(InputStream is) {
        Yaml yaml = new Yaml();
        Map<String, Object> data = yaml.load(is);

        this.pluginName = (String) data.get("name");
        this.mainClass = (String) data.get("main");
        this.version = (String) data.getOrDefault("version", "unknown");

        if (mainClass == null) {
            throw new RuntimeException("Invalid plugin.yml: missing 'main' field");
        }
    }

    // NEW: Extract base package from main class
    private void extractBasePackage() {
        // Example: com.zeltuv.swiftpay.SwiftpayPlugin → com.zeltuv.swiftpay
        int lastDot = mainClass.lastIndexOf('.');
        if (lastDot > 0) {
            this.basePackage = mainClass.substring(0, lastDot);
        } else {
            this.basePackage = "org.bukkit.plugin"; // Fallback
        }
    }

    private void detectJavaVersion(JarFile jar) throws IOException {
        String classPath = mainClass.replace('.', '/') + ".class";
        JarEntry classEntry = jar.getJarEntry(classPath);

        if (classEntry != null) {
            try (InputStream is = jar.getInputStream(classEntry);
                 DataInputStream dis = new DataInputStream(is)) {

                int magic = dis.readInt();
                if (magic != 0xCAFEBABE) {
                    throw new IOException("Invalid class file");
                }

                dis.readUnsignedShort();
                int majorVersion = dis.readUnsignedShort();
                this.javaVersion = majorVersionToJavaVersion(majorVersion);
            }
        } else {
            this.javaVersion = 8;
        }
    }

    private int majorVersionToJavaVersion(int majorVersion) {
        if (majorVersion >= 52) {
            return majorVersion - 44;
        }
        return 8;
    }

    // Getters
    public String getPluginName() { return pluginName; }
    public String getMainClass() { return mainClass; }
    public String getVersion() { return version; }
    public int getJavaVersion() { return javaVersion; }
    public String getBasePackage() { return basePackage; } // NEW
}
