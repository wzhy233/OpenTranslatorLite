package io.github.wzhy233.open_translator;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TranslatorTest {
    private static final TranslatorTestSupport TEST_SETUP = new TranslatorTestSupport();
    private static Translator translator;

    @BeforeClass
    public static void setUpClass() throws Exception {
        TEST_SETUP.beforeAll();
        translator = new Translator();
        translator.clearCache();
    }

    @AfterClass
    public static void tearDownClass() {
        if (translator != null) {
            translator.shutdown();
        }
        TEST_SETUP.afterAll();
    }

    @Test
    public void testSameLang() {
        String result = translator.translate("en", "en", "Hello");
        assertEquals("Hello", result);
    }

    @Test
    public void testEmptyContent() {
        String result = translator.translate("en", "zh", "");
        assertEquals("", result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnsupportedPair() {
        translator.translate("en", "fr", "test");
    }

    @Test
    public void testSupportedPairs() {
        assertTrue(translator.isSupportedPair("en-zh"));
        assertTrue(translator.isSupportedPair("zh-en"));
        assertTrue(translator.isSupportedPair("auto-zh"));
        assertTrue(translator.isSupportedPair("auto-en"));
    }

    @Test
    public void testCacheStatistics() {
        translator.clearCache();
        Translator.CacheStatistics stats = translator.getCacheStatistics();
        assertNotNull(stats);
        assertEquals(0, stats.totalEntries);
    }

    @Test
    public void testAsyncTranslation() throws Exception {
        Future<String> future = translator.translateAsync("zh", "en", "你好啊？？？我是OpenTranslatorLite");
        String result = future.get();
        assertNotNull(result);
        assertFalse(result.trim().isEmpty());
        assertFalse(result.contains("\uFFFD"));
    }

    @Test
    public void testBatchTranslation() {
        String[] results = translator.translateBatch("en", "zh", new String[]{"Hello", "World"});
        assertEquals(2, results.length);
        assertFalse(results[0].trim().isEmpty());
        assertFalse(results[1].trim().isEmpty());
        assertFalse(results[0].contains("\uFFFD"));
        assertFalse(results[1].contains("\uFFFD"));
    }

    @Test
    public void testAutoTranslation() {
        String result = translator.translate("auto", "zh", "Hello world");
        assertNotNull(result);
        assertFalse(result.trim().isEmpty());
        assertFalse(result.contains("\uFFFD"));
    }

    @Test
    public void testAutoKeepsTargetLanguageContent() {
        String result = translator.translate("auto", "zh", "你好，世界");
        assertEquals("你好，世界", result);
    }

    @Test
    public void testAutoBatchTranslation() {
        String[] results = translator.translateBatch("auto", "zh", new String[]{"Hello", "你好"});
        assertEquals(2, results.length);
        assertFalse(results[0].trim().isEmpty());
        assertEquals("你好", results[1]);
        assertFalse(results[0].contains("\uFFFD"));
    }

    @Test
    public void testTranslation() {
        String result = translator.translate("en", "zh",
                "Generation gap between parents and children misunderstanding between parents and children.");
        assertNotNull(result);
        assertFalse(result.trim().isEmpty());
        assertFalse(result.contains("\uFFFD"));
    }
}
