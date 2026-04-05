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
    private static final String AUTO_LANGUAGE = "auto";
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

        if (content.trim().isEmpty()) {
            return "";
        }

        String normalized = normalizeContent(content);
        sourceLang = resolveSourceLanguage(sourceLang, targetLang, normalized);

        if (sourceLang.equals(targetLang)) {
            return content;
        }

        String pair = sourceLang + "-" + targetLang;
        if (!isSupportedPair(pair)) {
            throw new IllegalArgumentException("Unsupported language pair: " + pair);
        }

        try {
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
        if (!AUTO_LANGUAGE.equals(sourceLang) && sourceLang.equals(targetLang)) {
            System.arraycopy(contents, 0, results, 0, contents.length);
            return results;
        }

        if (!AUTO_LANGUAGE.equals(sourceLang)) {
            String pair = sourceLang + "-" + targetLang;
            if (!isSupportedPair(pair)) {
                throw new IllegalArgumentException("Unsupported language pair: " + pair);
            }
        }

        Map<String, List<String>> uncachedContentsBySource = new LinkedHashMap<>();
        Map<String, Map<String, List<Integer>>> uncachedPositionsBySource = new LinkedHashMap<>();

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
            String resolvedSourceLang = resolveSourceLanguage(sourceLang, targetLang, normalized);
            if (resolvedSourceLang.equals(targetLang)) {
                results[i] = content;
                continue;
            }

            String cached = cache.get(resolvedSourceLang, targetLang, normalized);
            if (cached != null) {
                results[i] = cached;
                continue;
            }
            List<String> uncachedContents = uncachedContentsBySource.computeIfAbsent(
                    resolvedSourceLang, ignored -> new ArrayList<>());
            Map<String, List<Integer>> uncachedPositions = uncachedPositionsBySource.computeIfAbsent(
                    resolvedSourceLang, ignored -> new LinkedHashMap<>());
            List<Integer> positions = uncachedPositions.get(normalized);
            if (positions == null) {
                positions = new ArrayList<>();
                uncachedPositions.put(normalized, positions);
                uncachedContents.add(normalized);
            }
            positions.add(i);
        }

        if (uncachedContentsBySource.isEmpty()) {
            return results;
        }

        try {
            for (Map.Entry<String, List<String>> entry : uncachedContentsBySource.entrySet()) {
                String resolvedSourceLang = entry.getKey();
                List<String> uncachedContents = entry.getValue();
                String[] translated = model.translateBatch(resolvedSourceLang, targetLang,
                        uncachedContents.toArray(new String[0]));
                Map<String, List<Integer>> uncachedPositions = uncachedPositionsBySource.get(resolvedSourceLang);
                for (int i = 0; i < translated.length; i++) {
                    String normalized = uncachedContents.get(i);
                    String translatedText = translated[i];
                    List<Integer> positions = uncachedPositions.get(normalized);
                    for (int position : positions) {
                        results[position] = translatedText;
                    }
                    cache.put(resolvedSourceLang, targetLang, normalized, translatedText);
                }
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
        Set<String> pairs = new LinkedHashSet<>(supportedPairs);
        for (String pair : supportedPairs) {
            String[] parts = splitPair(pair);
            if (parts != null) {
                pairs.add(AUTO_LANGUAGE + "-" + parts[1]);
            }
        }
        return pairs.toArray(new String[0]);
    }

    public boolean isSupportedPair(String pair) {
        if (pair == null) {
            return false;
        }
        String normalizedPair = pair.toLowerCase().trim();
        if (supportedPairs.contains(normalizedPair)) {
            return true;
        }
        String[] parts = splitPair(normalizedPair);
        return parts != null
                && AUTO_LANGUAGE.equals(parts[0])
                && hasAnySourceForTarget(parts[1]);
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

    private String resolveSourceLanguage(String sourceLang, String targetLang, String content) {
        if (!AUTO_LANGUAGE.equals(sourceLang)) {
            return sourceLang;
        }
        Set<String> candidateLanguages = getCandidateSourceLanguages(targetLang);
        if (candidateLanguages.isEmpty()) {
            throw new IllegalArgumentException("Unsupported language pair: " + AUTO_LANGUAGE + "-" + targetLang);
        }

        LinkedHashSet<String> detectionCandidates = new LinkedHashSet<>(candidateLanguages);
        detectionCandidates.add(targetLang);
        return detectLanguage(content, detectionCandidates, targetLang);
    }

    private Set<String> getCandidateSourceLanguages(String targetLang) {
        Set<String> candidates = new LinkedHashSet<>();
        for (String pair : supportedPairs) {
            String[] parts = splitPair(pair);
            if (parts != null && targetLang.equals(parts[1])) {
                candidates.add(parts[0]);
            }
        }
        return candidates;
    }

    private boolean hasAnySourceForTarget(String targetLang) {
        for (String pair : supportedPairs) {
            String[] parts = splitPair(pair);
            if (parts != null && targetLang.equals(parts[1])) {
                return true;
            }
        }
        return false;
    }

    private static String[] splitPair(String pair) {
        String[] parts = pair.split("-", 2);
        return parts.length == 2 ? parts : null;
    }

    private static String detectLanguage(String content, Set<String> candidates, String fallbackLanguage) {
        Map<Character.UnicodeScript, Integer> scriptCounts = new EnumMap<>(Character.UnicodeScript.class);
        int latinLetters = 0;
        int hanLetters = 0;

        for (int i = 0; i < content.length(); ) {
            int codePoint = content.codePointAt(i);
            i += Character.charCount(codePoint);
            if (!Character.isLetter(codePoint)) {
                continue;
            }
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            scriptCounts.merge(script, 1, Integer::sum);
            if (script == Character.UnicodeScript.LATIN) {
                latinLetters++;
            } else if (script == Character.UnicodeScript.HAN) {
                hanLetters++;
            }
        }

        String bestLanguage = fallbackLanguage;
        int bestScore = -1;
        for (String candidate : candidates) {
            int score = scoreLanguage(candidate, scriptCounts, latinLetters, hanLetters);
            if (score > bestScore) {
                bestScore = score;
                bestLanguage = candidate;
            }
        }
        return bestLanguage;
    }

    private static int scoreLanguage(String language,
                                     Map<Character.UnicodeScript, Integer> scriptCounts,
                                     int latinLetters,
                                     int hanLetters) {
        switch (language) {
            case "zh":
                return scriptCounts.getOrDefault(Character.UnicodeScript.HAN, 0) * 100;
            case "en":
                return latinLetters * 100;
            case "ja":
                return scriptCounts.getOrDefault(Character.UnicodeScript.HIRAGANA, 0) * 100
                        + scriptCounts.getOrDefault(Character.UnicodeScript.KATAKANA, 0) * 100
                        + hanLetters * 10;
            case "ko":
                return scriptCounts.getOrDefault(Character.UnicodeScript.HANGUL, 0) * 100;
            case "ru":
            case "uk":
            case "bg":
            case "sr":
                return scriptCounts.getOrDefault(Character.UnicodeScript.CYRILLIC, 0) * 100;
            case "ar":
            case "fa":
            case "ur":
                return scriptCounts.getOrDefault(Character.UnicodeScript.ARABIC, 0) * 100;
            case "he":
                return scriptCounts.getOrDefault(Character.UnicodeScript.HEBREW, 0) * 100;
            case "hi":
            case "mr":
            case "ne":
                return scriptCounts.getOrDefault(Character.UnicodeScript.DEVANAGARI, 0) * 100;
            case "th":
                return scriptCounts.getOrDefault(Character.UnicodeScript.THAI, 0) * 100;
            case "el":
                return scriptCounts.getOrDefault(Character.UnicodeScript.GREEK, 0) * 100;
            default:
                return latinLetters;
        }
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
