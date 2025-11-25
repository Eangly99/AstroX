package dev.naruto.astrox.core;

import java.io.*;
import java.net.URL;
import java.nio.file.*;

public class JDKDownloader {
    private static final String JDK_CACHE_DIR = System.getProperty("user.home") + "/.astrox/jdks";

    public void ensureJDK(int requiredVersion) throws IOException {
        File jdkDir = new File(JDK_CACHE_DIR, "jdk-" + requiredVersion);

        // Check if JDK directory exists AND contains actual JDK files
        if (jdkDir.exists() && isValidJDK(jdkDir)) {
            System.out.println("[✓] JDK " + requiredVersion + " already cached");
            return;
        }

        System.out.println("[*] Downloading JDK " + requiredVersion + "...");

        // Create cache directory if it doesn't exist
        jdkDir.mkdirs();

        String downloadUrl = getJDKDownloadURL(requiredVersion);
        downloadAndExtract(downloadUrl, jdkDir);

        System.out.println("[✓] JDK " + requiredVersion + " ready");
    }

    /**
     * Validate that the JDK directory actually contains a JDK
     */
    private boolean isValidJDK(File jdkDir) {
        // Check for bin/javac or bin/javac.exe
        File javac = new File(jdkDir, "bin/javac");
        File javacExe = new File(jdkDir, "bin/javac.exe");
        return javac.exists() || javacExe.exists();
    }

    private String getJDKDownloadURL(int version) {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch");

        return String.format(
                "https://api.adoptium.net/v3/binary/latest/%d/ga/%s/%s/jdk/hotspot/normal/eclipse",
                version,
                os.contains("win") ? "windows" : (os.contains("mac") ? "mac" : "linux"),
                arch.contains("64") ? "x64" : "x86"
        );
    }

    private void downloadAndExtract(String url, File targetDir) throws IOException {
        File tempFile = File.createTempFile("jdk", ".tar.gz");

        try (InputStream in = new URL(url).openStream();
             FileOutputStream out = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }

        // Extract would go here (tar/unzip implementation)
        // For now, just create a marker file
        new File(targetDir, ".downloaded").createNewFile();

        tempFile.delete();
    }
}
