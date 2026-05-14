package dev.naruto.astrox.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Maintains a database of known plugin fingerprints (SHA-256 hashes of class files)
 * to warn operators before injecting into critical/well-known plugins.
 *
 * <p>Database stored at {@code ~/.astrox/known_plugins.json}</p>
 */
public class PluginFingerprinter {

    private static final Logger LOG = LoggerFactory.getLogger(PluginFingerprinter.class);
    private static final String DB_DIR = System.getProperty("user.home") + "/.astrox";
    private static final String DB_FILE = "known_plugins.json";

    private final ObjectMapper mapper;
    private final Map<String, PluginFingerprint> database;
    private final File dbFile;

    public PluginFingerprinter() {
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.database = new LinkedHashMap<>();
        this.dbFile = new File(DB_DIR, DB_FILE);
        loadDatabase();
        ensureDefaults();
    }

    /**
     * Compute fingerprint for a JAR file.
     * SHA-256 hash of each .class file, combined into a composite hash.
     */
    public String computeFingerprint(File jarFile) throws IOException {
        try (JarFile jar = new JarFile(jarFile)) {
            MessageDigest composite = MessageDigest.getInstance("SHA-256");

            List<String> classEntries = jar.stream()
                    .filter(e -> e.getName().endsWith(".class"))
                    .map(JarEntry::getName)
                    .sorted()
                    .toList();

            for (String entryName : classEntries) {
                JarEntry entry = jar.getJarEntry(entryName);
                try (InputStream is = jar.getInputStream(entry)) {
                    byte[] classBytes = is.readAllBytes();
                    MessageDigest classDigest = MessageDigest.getInstance("SHA-256");
                    byte[] classHash = classDigest.digest(classBytes);
                    composite.update(classHash);
                }
            }

            return bytesToHex(composite.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 not available", e);
        }
    }

    /**
     * Check if a JAR matches a known-safe plugin fingerprint.
     *
     * @return Optional containing the match info, or empty if no match
     */
    public Optional<PluginFingerprint> checkKnownSafe(File jarFile) {
        try {
            String fingerprint = computeFingerprint(jarFile);
            for (PluginFingerprint known : database.values()) {
                if (known.fingerprints().contains(fingerprint)) {
                    return Optional.of(known);
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed to compute fingerprint for {}: {}", jarFile.getName(), e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Add a plugin fingerprint to the database.
     */
    public void addFingerprint(String pluginName, String version, File jarFile) throws IOException {
        String fingerprint = computeFingerprint(jarFile);
        String key = pluginName.toLowerCase();

        PluginFingerprint existing = database.get(key);
        Set<String> fingerprints;
        if (existing != null) {
            fingerprints = new LinkedHashSet<>(existing.fingerprints());
            fingerprints.add(fingerprint);
        } else {
            fingerprints = new LinkedHashSet<>();
            fingerprints.add(fingerprint);
        }

        database.put(key, new PluginFingerprint(pluginName, version, fingerprints, "user-added"));
        saveDatabase();
        LOG.info("Added fingerprint for {} v{}: {}", pluginName, version,
                fingerprint.substring(0, 16) + "...");
    }

    /**
     * List all known plugin fingerprints.
     */
    public Map<String, PluginFingerprint> getDatabase() {
        return Collections.unmodifiableMap(database);
    }

    // ==================== Persistence ====================

    private void loadDatabase() {
        if (!dbFile.exists()) {
            LOG.debug("No fingerprint database found at {}", dbFile.getAbsolutePath());
            return;
        }

        try {
            Map<String, PluginFingerprint> loaded = mapper.readValue(dbFile,
                    new TypeReference<LinkedHashMap<String, PluginFingerprint>>() {});
            database.putAll(loaded);
            LOG.debug("Loaded {} known plugin fingerprints", database.size());
        } catch (IOException e) {
            LOG.warn("Failed to load fingerprint database: {}", e.getMessage());
        }
    }

    private void saveDatabase() {
        try {
            Files.createDirectories(Path.of(DB_DIR));
            mapper.writeValue(dbFile, database);
        } catch (IOException e) {
            LOG.error("Failed to save fingerprint database", e);
        }
    }

    /**
     * Pre-populate with known critical plugin names (fingerprints are placeholder —
     * real fingerprints would be computed from actual JARs).
     */
    private void ensureDefaults() {
        if (database.containsKey("protocollib")) return;

        String[][] defaults = {
                {"protocollib", "ProtocolLib", "5.x"},
                {"luckperms", "LuckPerms", "5.x"},
                {"vault", "Vault", "1.7.x"},
                {"viaversion", "ViaVersion", "4.x"},
                {"worldedit", "WorldEdit", "7.x"},
                {"worldguard", "WorldGuard", "7.x"},
                {"essentials", "Essentials", "2.x"},
        };

        for (String[] def : defaults) {
            database.putIfAbsent(def[0], new PluginFingerprint(
                    def[1], def[2], Set.of(), "builtin-placeholder"
            ));
        }

        saveDatabase();
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Plugin fingerprint record.
     */
    public record PluginFingerprint(
            String name,
            String version,
            Set<String> fingerprints,
            String source
    ) {}
}
