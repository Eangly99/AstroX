package dev.naruto.astrox.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Value object carrying the complete result of an injection pipeline run.
 * Serializable to JSON via Jackson for the {@code --report} flag.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PipelineResult(
        String originalJarPath,
        String outputJarPath,
        AnalysisSummary analysis,
        List<String> injectedClasses,
        List<String> warnings,
        boolean dryRun,
        boolean stealthMode,
        long durationMs,
        String fingerprint,
        String encryptionKeyHex
) {
    public PipelineResult {
        injectedClasses = Collections.unmodifiableList(new ArrayList<>(injectedClasses));
        warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
    }
    /**
     * Analysis summary sub-record.
     */
    public record AnalysisSummary(
            String pluginName,
            String version,
            String mainClass,
            int javaVersion,
            String basePackage
    ) {}

    /**
     * Mutable builder for constructing PipelineResult during pipeline execution.
     */
    public static class Builder {
        private String originalJarPath;
        private String outputJarPath;
        private String pluginName;
        private String version;
        private String mainClass;
        private int javaVersion;
        private String basePackage;
        private final List<String> injectedClasses = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private boolean dryRun;
        private boolean stealthMode;
        private long startTime;
        private String fingerprint;
        private String encryptionKeyHex;

        public Builder() {
            this.startTime = System.currentTimeMillis();
        }

        public Builder originalJarPath(String path) { this.originalJarPath = path; return this; }
        public Builder outputJarPath(String path) { this.outputJarPath = path; return this; }
        public Builder pluginName(String name) { this.pluginName = name; return this; }
        public Builder version(String ver) { this.version = ver; return this; }
        public Builder mainClass(String cls) { this.mainClass = cls; return this; }
        public Builder javaVersion(int ver) { this.javaVersion = ver; return this; }
        public Builder basePackage(String pkg) { this.basePackage = pkg; return this; }
        public Builder dryRun(boolean dry) { this.dryRun = dry; return this; }
        public Builder stealthMode(boolean stealth) { this.stealthMode = stealth; return this; }
        public Builder fingerprint(String fp) { this.fingerprint = fp; return this; }
        public Builder encryptionKeyHex(String key) { this.encryptionKeyHex = key; return this; }

        public Builder addInjectedClass(String className) {
            injectedClasses.add(className);
            return this;
        }

        public Builder addWarning(String warning) {
            warnings.add(warning);
            return this;
        }

        public List<String> getWarnings() { return Collections.unmodifiableList(warnings); }
        public List<String> getInjectedClasses() { return Collections.unmodifiableList(injectedClasses); }

        public PipelineResult build() {
            long duration = System.currentTimeMillis() - startTime;
            return new PipelineResult(
                    originalJarPath, outputJarPath,
                    new AnalysisSummary(pluginName, version, mainClass, javaVersion, basePackage),
                    List.copyOf(injectedClasses), List.copyOf(warnings),
                    dryRun, stealthMode, duration, fingerprint, encryptionKeyHex
            );
        }
    }

    /**
     * Serialize to JSON string.
     */
    public String toJson() throws IOException {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        return mapper.writeValueAsString(this);
    }

    /**
     * Write JSON report to file.
     */
    public void writeReport(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(file, this);
    }
}
