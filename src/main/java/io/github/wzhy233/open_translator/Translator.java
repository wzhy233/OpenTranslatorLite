package io.github.wzhy233.open_translator;

import io.github.wzhy233.open_translator.cache.CacheManager;
import io.github.wzhy233.open_translator.config.ConfigManager;
import io.github.wzhy233.open_translator.model.TranslationModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;


public class Translator {
    private static final Logger logger = LoggerFactory.getLogger(Translator.class);
    private static final AtomicBoolean bannerPrinted = new AtomicBoolean(false);
    private static final String STARTUP_BANNER =
            "\n" +
                    " ██████╗ ██████╗ ███████╗███╗   ██╗████████╗██████╗  █████╗ ███╗   ██╗███████╗██╗      █████╗ ████████╗ ██████╗ ██████╗ \n" +
                    "██╔═══██╗██╔══██╗██╔════╝████╗  ██║╚══██╔══╝██╔══██╗██╔══██╗████╗  ██║██╔════╝██║     ██╔══██╗╚══██╔══╝██╔═══██╗██╔══██╗\n" +
                    "██║   ██║██████╔╝█████╗  ██╔██╗ ██║   ██║   ██████╔╝███████║██╔██╗ ██║███████╗██║     ███████║   ██║   ██║   ██║██████╔╝\n" +
                    "██║   ██║██╔═══╝ ██╔══╝  ██║╚██╗██║   ██║   ██╔══██╗██╔══██║██║╚██╗██║╚════██║██║     ██╔══██║   ██║   ██║   ██║██╔══██╗\n" +
                    "╚██████╔╝██║     ███████╗██║ ╚████║   ██║   ██║  ██║██║  ██║██║ ╚████║███████║███████╗██║  ██║   ██║   ╚██████╔╝██║  ██║\n" +
                    " ╚═════╝ ╚═╝     ╚══════╝╚═╝  ╚═══╝   ╚═╝   ╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═══╝╚══════╝╚══════╝╚═╝  ╚═╝   ╚═╝    ╚═════╝ ╚═╝  ╚═╝\n" +
                    "\n" +
                    BuildInfo.getBannerLine() +
                    "                         GitHub: https://github.com/wzhy233/OpenTranslatorLite\n" +
                    "                         Author: wzhy233\n";

    private final TranslationModel model;
    private final CacheManager cache;
    private final ExecutorService executorService;
    private volatile Set<String> supportedPairs = Collections.emptySet();
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);
    /**
     * 创建翻译器
     */
    public Translator() {
        this(ConfigManager.getCachePath(), TranslationModel.defaultWorkerCount());
    }

    /**
     * 创建翻译器
     *
     * @param cachePath 缓存目录
     */
    public Translator(String cachePath) {
        this(cachePath, TranslationModel.defaultWorkerCount());
    }

    /**
     * 创建翻译器
     *
     * @param cachePath 缓存目录
     * @param threadPoolSize 线程池大小
     */
    public Translator(String cachePath, int threadPoolSize) {
        if (threadPoolSize <= 0) threadPoolSize = TranslationModel.defaultWorkerCount();

        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
        this.cache = new CacheManager(cachePath);
        this.model = new TranslationModel();

        initialize();
    }

    private void initialize() {
        try {
            printBanner();
            cache.initialize();
            model.initialize();
            supportedPairs = new HashSet<>(model.getSupportedPairs());
            isInitialized.set(true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize", e);
        }
    }

    private static void printBanner() {
        if (bannerPrinted.compareAndSet(false, true)) {
            System.out.print(STARTUP_BANNER);
        }
    }

    /**
     * 翻译文本
     *
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @param content 原文
     * @return 译文
     */
    public String translate(String sourceLang, String targetLang, String content) {
        checkState();

        if (sourceLang == null || targetLang == null || content == null) {
            throw new IllegalArgumentException("Arguments must not be null");
        }

        sourceLang = sourceLang.toLowerCase().trim();
        targetLang = targetLang.toLowerCase().trim();

        if (sourceLang.equals(targetLang)) {
            return content;
        }

        if (content.trim().isEmpty()) {
            return "";
        }

        String pair = sourceLang + "-" + targetLang;
        if (!isSupportedPair(pair)) {
            throw new IllegalArgumentException("Unsupported language pair: " + pair);
        }

        try {
            String normalized = normalizeContent(content);

            String cached = cache.get(sourceLang, targetLang, normalized);
            if (cached != null) {
                return cached;
            }

            String result = model.translate(sourceLang, targetLang, normalized);
            cache.put(sourceLang, targetLang, normalized, result);

            return result;
        } catch (Exception e) {
            logger.error("Translation failed", e);
            throw new RuntimeException("Translation failed: " + e.getMessage(), e);
        }
    }

    /**
     * 异步翻译
     *
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @param content 原文
     * @return Future 结果
     */
    public Future<String> translateAsync(String sourceLang, String targetLang, String content) {
        checkState();
        return executorService.submit(() -> translate(sourceLang, targetLang, content));
    }

    /**
     * 批量翻译
     *
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @param contents 原文列表
     * @return 译文列表
     */
    public String[] translateBatch(String sourceLang, String targetLang, String[] contents) {
        checkState();
        if (contents == null) {
            throw new IllegalArgumentException("Contents must not be null");
        }
        if (contents.length == 0) {
            return new String[0];
        }

        if (sourceLang == null || targetLang == null) {
            throw new IllegalArgumentException("Arguments must not be null");
        }

        sourceLang = sourceLang.toLowerCase().trim();
        targetLang = targetLang.toLowerCase().trim();

        String[] results = new String[contents.length];
        if (sourceLang.equals(targetLang)) {
            System.arraycopy(contents, 0, results, 0, contents.length);
            return results;
        }

        String pair = sourceLang + "-" + targetLang;
        if (!isSupportedPair(pair)) {
            throw new IllegalArgumentException("Unsupported language pair: " + pair);
        }

        List<String> uncachedContents = new ArrayList<>(contents.length);
        Map<String, List<Integer>> uncachedPositions = new LinkedHashMap<>();

        for (int i = 0; i < contents.length; i++) {
            String content = contents[i];
            if (content == null) {
                throw new IllegalArgumentException("Contents must not contain null values");
            }
            if (content.trim().isEmpty()) {
                results[i] = "";
                continue;
            }

            String normalized = normalizeContent(content);
            String cached = cache.get(sourceLang, targetLang, normalized);
            if (cached != null) {
                results[i] = cached;
                continue;
            }
            List<Integer> positions = uncachedPositions.get(normalized);
            if (positions == null) {
                positions = new ArrayList<>();
                uncachedPositions.put(normalized, positions);
                uncachedContents.add(normalized);
            }
            positions.add(i);
        }

        if (uncachedContents.isEmpty()) {
            return results;
        }

        try {
            String[] translated = model.translateBatch(sourceLang, targetLang,
                    uncachedContents.toArray(new String[0]));
            for (int i = 0; i < translated.length; i++) {
                String normalized = uncachedContents.get(i);
                String translatedText = translated[i];
                List<Integer> positions = uncachedPositions.get(normalized);
                for (int position : positions) {
                    results[position] = translatedText;
                }
                cache.put(sourceLang, targetLang, normalized, translatedText);
            }
            return results;
        } catch (Exception e) {
            logger.error("Batch translation failed", e);
            throw new RuntimeException("Batch translation failed: " + e.getMessage(), e);
        }
    }

    /**
     * 获取语言对
     *
     * @return 语言对列表
     */
    public String[] getSupportedLanguagePairs() {
        return supportedPairs.toArray(new String[0]);
    }

    public boolean isSupportedPair(String pair) {
        return pair != null && supportedPairs.contains(pair);
    }

    /**
     * 获取缓存统计
     *
     * @return 缓存统计
     */
    public CacheStatistics getCacheStatistics() {
        checkState();
        return cache.getStatistics();
    }

    /**
     * 清空缓存
     */
    public void clearCache() {
        checkState();
        cache.clear();
    }

    public String getCachePath() {
        return cache.getCachePath();
    }

    /**
     * 设置缓存目录
     *
     * @param newCachePath 新缓存目录
     */
    public void setCachePath(String newCachePath) {
        checkState();
        cache.setCachePath(newCachePath);
        ConfigManager.setCachePath(newCachePath);
    }

    /**
     * 关闭翻译器
     */
    public void shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            if (executorService != null && !executorService.isShutdown()) {
                executorService.shutdown();
                try {
                    if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                        executorService.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executorService.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            if (cache != null) {
                cache.close();
            }
            if (model != null) {
                model.close();
            }
        }
    }

    private static String normalizeContent(String content) {
        String trimmed = content.trim();
        StringBuilder normalized = new StringBuilder(trimmed.length());
        boolean lastWasWhitespace = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (Character.isWhitespace(ch)) {
                if (!lastWasWhitespace) {
                    normalized.append(' ');
                    lastWasWhitespace = true;
                }
            } else {
                normalized.append(ch);
                lastWasWhitespace = false;
            }
        }
        return normalized.toString();
    }

    private void checkState() {
        if (isShutdown.get()) throw new IllegalStateException("Translator is closed");
        if (!isInitialized.get()) throw new IllegalStateException("Translator is not initialized");
    }

    public static String getVersion() {
        return BuildInfo.getVersion();
    }

    public static String getBuildInfo() {
        return BuildInfo.getBuildInfo();
    }

    public static class CacheStatistics {
        public final int totalEntries;
        public final long cacheSizeBytes;
        public final int hits;
        public final int misses;
        public final double hitRate;

        public CacheStatistics(int entries, long size, int hits, int misses) {
            this.totalEntries = entries;
            this.cacheSizeBytes = size;
            this.hits = hits;
            this.misses = misses;
            this.hitRate = (hits + misses) > 0 ? (double) hits / (hits + misses) : 0;
        }

        @Override
        public String toString() {
            return String.format(
                    "CacheStatistics{entries=%d, size=%.2fMB, hits=%d, misses=%d, hitRate=%.2f%%}",
                    totalEntries, cacheSizeBytes / 1024.0 / 1024.0, hits, misses, hitRate * 100
            );
        }
    }
}
