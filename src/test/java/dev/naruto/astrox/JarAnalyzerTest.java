package dev.naruto.astrox;

import dev.naruto.astrox.core.JarAnalyzer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JarAnalyzer using programmatically-built dummy plugin JARs.
 */
class JarAnalyzerTest {

    @TempDir
    Path tempDir;

    private File dummyPluginJar;
    private File nonPluginJar;
    private File corruptFile;

    @BeforeEach
    void setUp() throws Exception {
        dummyPluginJar = tempDir.resolve("DummyPlugin.jar").toFile();
        TestUtils.createDummyPluginJar(dummyPluginJar, "DummyPlugin",
                "com.test.DummyPlugin", "1.0");

        nonPluginJar = tempDir.resolve("NotAPlugin.jar").toFile();
        TestUtils.createNonPluginJar(nonPluginJar);

        corruptFile = tempDir.resolve("corrupt.jar").toFile();
        TestUtils.createCorruptFile(corruptFile);
    }

    @Test
    @DisplayName("Should analyze a valid plugin JAR correctly")
    void testAnalyzeValidPlugin() throws IOException {
        JarAnalyzer analyzer = new JarAnalyzer(dummyPluginJar);
        analyzer.analyze();

        assertEquals("DummyPlugin", analyzer.getPluginName());
        assertEquals("com.test.DummyPlugin", analyzer.getMainClass());
        assertEquals("1.0", analyzer.getVersion());
        assertNotNull(analyzer.getBasePackage());
    }

    @Test
    @DisplayName("Should extract base package from main class")
    void testAnalyzeBasePackage() throws IOException {
        JarAnalyzer analyzer = new JarAnalyzer(dummyPluginJar);
        analyzer.analyze();

        assertEquals("com.test", analyzer.getBasePackage());
    }

    @Test
    @DisplayName("Should detect Java version from class files")
    void testDetectJavaVersion() throws IOException {
        JarAnalyzer analyzer = new JarAnalyzer(dummyPluginJar);
        analyzer.analyze();

        // Our dummy class is compiled with V17 (major version 61)
        assertTrue(analyzer.getJavaVersion() >= 17,
                "Expected Java >= 17, got: " + analyzer.getJavaVersion());
    }

    @Test
    @DisplayName("Should throw IOException for JAR without plugin.yml")
    void testAnalyzeNoPluginYml() {
        JarAnalyzer analyzer = new JarAnalyzer(nonPluginJar);
        assertThrows(IOException.class, analyzer::analyze,
                "Should throw IOException when plugin.yml is missing");
    }

    @Test
    @DisplayName("Should throw exception for corrupt/invalid JAR")
    void testAnalyzeCorruptFile() {
        JarAnalyzer analyzer = new JarAnalyzer(corruptFile);
        assertThrows(Exception.class, analyzer::analyze,
                "Should throw exception for corrupt file");
    }

    @Test
    @DisplayName("Should handle plugin with deeply nested package")
    void testDeepPackage() throws Exception {
        File deepJar = tempDir.resolve("DeepPlugin.jar").toFile();
        TestUtils.createDummyPluginJar(deepJar, "DeepPlugin",
                "com.example.deep.nested.pkg.DeepPlugin", "2.5.1");

        JarAnalyzer analyzer = new JarAnalyzer(deepJar);
        analyzer.analyze();

        assertEquals("com.example.deep.nested.pkg", analyzer.getBasePackage());
        assertEquals("DeepPlugin", analyzer.getPluginName());
        assertEquals("2.5.1", analyzer.getVersion());
    }
}
