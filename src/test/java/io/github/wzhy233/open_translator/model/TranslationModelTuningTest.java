package io.github.wzhy233.open_translator.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TranslationModelTuningTest {
    @Test
    public void shouldCapBatchSizeByRequestSize() {
        String previous = System.getProperty("open_translator.max_batch_size");
        System.setProperty("open_translator.max_batch_size", "16");
        try {
            assertEquals(3, TranslationModel.resolveBatchSize(3));
            assertEquals(1, TranslationModel.resolveBatchSize(0));
        } finally {
            restoreProperty("open_translator.max_batch_size", previous);
        }
    }

    @Test
    public void shouldScaleWorkerCountFromCpuCount() {
        String previous = System.getProperty("open_translator.worker_count");
        System.clearProperty("open_translator.worker_count");
        try {
            assertEquals(1, TranslationModel.resolveWorkerCount(1));
            assertEquals(1, TranslationModel.resolveWorkerCount(3));
            assertEquals(2, TranslationModel.resolveWorkerCount(4));
            assertEquals(4, TranslationModel.resolveWorkerCount(16));
        } finally {
            restoreProperty("open_translator.worker_count", previous);
        }
    }

    @Test
    public void shouldHonorExplicitThreadOverrides() {
        String previous = System.getProperty("open_translator.intra_threads");
        System.setProperty("open_translator.intra_threads", "7");
        try {
            assertEquals(7, TranslationModel.resolveIntraThreads(4));
        } finally {
            restoreProperty("open_translator.intra_threads", previous);
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
