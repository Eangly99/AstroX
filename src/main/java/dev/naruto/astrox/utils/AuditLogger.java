package dev.naruto.astrox.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/**
 * Tamper-evident injection audit logger.
 *
 * <p>Writes JSON-line entries to {@code ~/.astrox/audit.log}. Each entry is
 * HMAC-SHA256 signed using a key derived from the master key, creating a
 * tamper-evident chain that can be verified with {@code --verify-audit}.</p>
 */
public final class AuditLogger {

    private static final Logger LOG = LoggerFactory.getLogger(AuditLogger.class);
    private static final String AUDIT_DIR = System.getProperty("user.home") + "/.astrox";
    private static final String AUDIT_FILE = "audit.log";
    private static final String HMAC_ALGO = "HmacSHA256";

    private final ObjectMapper mapper;
    private final File auditFile;
    private final byte[] hmacKey;

    /**
     * Create audit logger with key derived from master key.
     *
     * @param masterKey the master key used to derive HMAC signing key
     */
    public AuditLogger(String masterKey) {
        this.mapper = new ObjectMapper();
        this.auditFile = new File(AUDIT_DIR, AUDIT_FILE);
        this.hmacKey = deriveKey(masterKey);

        try {
            Files.createDirectories(Path.of(AUDIT_DIR));
        } catch (IOException e) {
            LOG.error("Failed to create audit directory", e);
        }
    }

    /**
     * Log an injection event.
     */
    public void logInjection(File targetJar, File outputJar,
                             List<String> modifiedClasses, String operatorId) {
        try {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("timestamp", Instant.now().toString());
            entry.put("event", "injection");
            entry.put("targetSha256", sha256(targetJar));
            entry.put("outputSha256", sha256(outputJar));
            entry.put("modifiedClasses", modifiedClasses);
            entry.put("operator", operatorId);
            entry.put("targetFile", targetJar.getName());
            entry.put("outputFile", outputJar.getName());

            // Serialize entry to JSON
            String json = mapper.writeValueAsString(entry);

            // Compute HMAC
            String hmac = computeHmac(json);

            // Write JSON line with HMAC appended
            String signedLine = json + "|HMAC:" + hmac;

            try (FileWriter fw = new FileWriter(auditFile, StandardCharsets.UTF_8, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                pw.println(signedLine);
            }

            LOG.debug("Audit entry logged for {}", targetJar.getName());

        } catch (Exception e) {
            LOG.error("Failed to write audit log", e);
        }
    }

    /**
     * Verify all audit log entries.
     *
     * @return verification result with details
     */
    public VerificationResult verifyAll() {
        List<String> errors = new ArrayList<>();
        int totalEntries = 0;
        int validEntries = 0;

        if (!auditFile.exists()) {
            return new VerificationResult(0, 0, List.of("Audit file not found"));
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(auditFile, StandardCharsets.UTF_8))) {
            String line;
            int lineNum = 0;

            while ((line = reader.readLine()) != null) {
                lineNum++;
                totalEntries++;

                int hmacSep = line.lastIndexOf("|HMAC:");
                if (hmacSep < 0) {
                    errors.add("Line " + lineNum + ": missing HMAC signature");
                    continue;
                }

                String json = line.substring(0, hmacSep);
                String storedHmac = line.substring(hmacSep + 6);

                String computedHmac = computeHmac(json);
                if (computedHmac.equals(storedHmac)) {
                    validEntries++;
                } else {
                    errors.add("Line " + lineNum + ": HMAC verification FAILED — entry tampered!");
                }
            }

        } catch (Exception e) {
            errors.add("Failed to read audit file: " + e.getMessage());
        }

        return new VerificationResult(totalEntries, validEntries, errors);
    }

    /**
     * Audit verification result.
     */
    public record VerificationResult(int totalEntries, int validEntries, List<String> errors) {
        public VerificationResult {
            errors = Collections.unmodifiableList(new ArrayList<>(errors));
        }

        public boolean isClean() {
            return errors.isEmpty() && totalEntries == validEntries;
        }
    }

    // ==================== Crypto utilities ====================

    private String computeHmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(hmacKey, HMAC_ALGO));
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    private byte[] deriveKey(String masterKey) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(("astrox-audit-" + masterKey).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Key derivation failed", e);
        }
    }

    private String sha256(File file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    md.update(buffer, 0, read);
                }
            }
            return bytesToHex(md.digest());
        } catch (Exception e) {
            return "error:" + e.getMessage();
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
