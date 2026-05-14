package dev.naruto.astrox;

import dev.naruto.astrox.core.BatchProcessor;
import dev.naruto.astrox.core.PipelineResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for concurrent batch processing of multiple JARs.
 */
class BatchProcessorTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        Config.setMasterKey("batch-test-key");
        RuntimeConfig.dryRun = false;
        RuntimeConfig.stealthMode = false;

        // Create 5 dummy plugin JARs
        for (int i = 1; i <= 5; i++) {
            File jar = tempDir.resolve("Plugin" + i + ".jar").toFile();
            TestUtils.createDummyPluginJar(jar, "Plugin" + i,
                    "com.test.plugin" + i + ".Main", "1." + i);
        }
    }

    @Test
    @DisplayName("Batch dry-run should process all JARs without output files")
    void testBatchDryRun() throws Exception {
        BatchProcessor processor = new BatchProcessor(tempDir.toFile(), 2, true, false);
        List<PipelineResult> results = processor.processAll();

        assertEquals(5, results.size(), "Should process all 5 JARs");
        for (PipelineResult result : results) {
            assertTrue(result.dryRun());
            assertFalse(result.injectedClasses().isEmpty());
        }

        // No output files should exist
        long outputCount = java.util.Arrays.stream(
                tempDir.toFile().listFiles((dir, name) -> name.endsWith("-astrox.jar"))
        ).count();
        assertEquals(0, outputCount, "Dry-run should produce no output files");
    }

    @Test
    @DisplayName("Batch processing should handle concurrent injection")
    void testBatchConcurrentInjection() throws Exception {
        BatchProcessor processor = new BatchProcessor(tempDir.toFile(), 4, false, false);
        List<PipelineResult> results = processor.processAll();

        assertEquals(5, results.size(), "Should produce 5 results");

        // Verify output files exist
        for (PipelineResult result : results) {
            assertFalse(result.dryRun());
            assertNotNull(result.analysis().pluginName());
            assertTrue(result.injectedClasses().size() > 0);
        }

        // Count output JARs
        File[] outputs = tempDir.toFile().listFiles(
                (dir, name) -> name.endsWith("-astrox.jar"));
        assertNotNull(outputs);
        assertEquals(5, outputs.length, "Should produce 5 output JARs");
    }
}
