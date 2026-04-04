package io.github.wzhy233.open_translator.cache;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CacheManagerTest {
    private static final String FLUSH_THRESHOLD_PROP = "open_translator.cache_index_flush_threshold";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldDelayIndexWritesUntilFlush() throws Exception {
        String previous = System.getProperty(FLUSH_THRESHOLD_PROP);
        System.setProperty(FLUSH_THRESHOLD_PROP, "100");
        try {
            Path cacheDir = temporaryFolder.newFolder("cache-delayed").toPath();
            CacheManager cacheManager = new CacheManager(cacheDir.toString());
            cacheManager.initialize();

            cacheManager.put("en", "zh", "hello", "你好");

            assertFalse(Files.exists(cacheDir.resolve("index.json")));

            cacheManager.flushIndex();

            assertTrue(Files.exists(cacheDir.resolve("index.json")));
        } finally {
            restoreProperty(previous);
        }
    }

    @Test
    public void shouldFlushIndexOnClose() throws Exception {
        String previous = System.getProperty(FLUSH_THRESHOLD_PROP);
        System.setProperty(FLUSH_THRESHOLD_PROP, "100");
        try {
            Path cacheDir = temporaryFolder.newFolder("cache-close").toPath();
            CacheManager cacheManager = new CacheManager(cacheDir.toString());
            cacheManager.initialize();

            cacheManager.put("en", "zh", "world", "世界");
            cacheManager.close();

            Path indexPath = cacheDir.resolve("index.json");
            assertTrue(Files.exists(indexPath));
            String indexJson = new String(Files.readAllBytes(indexPath), StandardCharsets.UTF_8);
            assertTrue(indexJson.contains(".txt"));
        } finally {
            restoreProperty(previous);
        }
    }

    private static void restoreProperty(String value) {
        if (value == null) {
            System.clearProperty(FLUSH_THRESHOLD_PROP);
        } else {
            System.setProperty(FLUSH_THRESHOLD_PROP, value);
        }
    }
}
