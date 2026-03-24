package io.github.wzhy233.open_translator;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.Future;

import static org.junit.Assert.*;

public class TranslatorTest {
    private static Translator translator;

    @BeforeClass
    public static void setUpClass() {
        translator = new Translator();
        translator.clearCache();
    }

    @AfterClass
    public static void tearDownClass() {
        if (translator != null) {
            translator.shutdown();
        }
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
    }

    @Test
    public void testBatchTranslation() {
        String[] results = translator.translateBatch("en", "zh", new String[]{"Hello", "World"});
        assertEquals(2, results.length);
        assertFalse(results[0].trim().isEmpty());
        assertFalse(results[1].trim().isEmpty());
    }

    @Test
    public void testTranslation() {
        String result = translator.translate("en", "zh", "Generation gap between parents and children misunderstanding between parents and children which is so- called generation gap. It is estimated that ( 75 percentages of parents often complain their children’s unreasonable behavior while children usually think their parents too old fashioned ). Why have there been so much misunderstanding between parents and children? Maybe the reasons can be listed as follows. The first one is that ( the two generations, having grown up at different times, have different likes and dislikes , thus the disagreement often rises between them ). Besides ( due to having little in common to talk about, they are not willing to sit face to face ). The third reason is ( with the pace of modern life becoming faster and faster, both of them are so busy with their work or study that they don’t spare enough time to exchange ideas ). To sum up , the main cause of XX is due to ( lake of communication and understanding each other ).It is high time that something was done upon it. For one thing ( children should respect their parents ). On the other hand , ( parents also should show solicitue for their children ). All these measures will certainly bridge the generation gap.");
        System.out.println(result);
        assertNotNull(result);
        assertFalse(result.trim().isEmpty());
    }
}
