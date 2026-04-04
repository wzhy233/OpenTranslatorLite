package io.github.wzhy233.open_translator.cache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.github.wzhy233.open_translator.Translator.CacheStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class CacheManager implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(CacheManager.class);

    private static final String INDEX_FILE = "index.json";
    private static final Gson gson = new GsonBuilder().create();
    private static final String INDEX_FLUSH_THRESHOLD_PROP = "open_translator.cache_index_flush_threshold";
    private static final int DEFAULT_INDEX_FLUSH_THRESHOLD = 32;
    private static final ThreadLocal<MessageDigest> SHA_256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    });

    private String cachePath;
    private final Map<String, CacheEntry> cacheIndex = new ConcurrentHashMap<>();
    private final Map<String, String> memoryCache = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();
    private final AtomicInteger pendingIndexWrites = new AtomicInteger();
    private volatile boolean indexDirty;

    public CacheManager(String cachePath) {
        this.cachePath = cachePath;
    }

    public void initialize() {
        lock.writeLock().lock();
        try {
            File cacheDir = new File(cachePath);
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            loadIndex();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String get(String sourceLang, String targetLang, String content) {
        lock.readLock().lock();
        try {
            String key = generateKey(sourceLang, targetLang, content);
            String cachedValue = memoryCache.get(key);
            if (cachedValue != null) {
                hits.increment();
                return cachedValue;
            }
            CacheEntry entry = cacheIndex.get(key);

            if (entry != null) {
                Path file = Paths.get(cachePath, entry.fileName);
                if (Files.exists(file)) {
                    try {
                        String result = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                        memoryCache.put(key, result);
                        hits.increment();
                        return result;
                    } catch (IOException e) {
                        logger.warn("Failed to read cache entry", e);
                    }
                }
            }
            misses.increment();
        } finally {
            lock.readLock().unlock();
        }
        return null;
    }

    public void put(String sourceLang, String targetLang, String content, String result) {
        lock.writeLock().lock();
        try {
            String key = generateKey(sourceLang, targetLang, content);

            if (cacheIndex.containsKey(key)) {
                return;
            }

            String fileName = key + ".txt";
            Path filePath = Paths.get(cachePath, fileName);

            try {
                Files.write(filePath, result.getBytes(StandardCharsets.UTF_8));
                CacheEntry entry = new CacheEntry(sourceLang, targetLang, fileName);
                cacheIndex.put(key, entry);
                memoryCache.put(key, result);
                markIndexDirty();
            } catch (IOException e) {
                logger.error("Failed to write cache entry", e);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private String generateKey(String sourceLang, String targetLang, String content) {
        try {
            String input = sourceLang + "|" + targetLang + "|" + content;
            MessageDigest digest = SHA_256.get();
            digest.reset();
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            return String.valueOf(content.hashCode());
        }
    }

    private void loadIndex() {
        String indexPath = cachePath + File.separator + INDEX_FILE;
        File indexFile = new File(indexPath);

        if (indexFile.exists()) {
            try {
                String json = new String(Files.readAllBytes(Paths.get(indexPath)), StandardCharsets.UTF_8);
                Map<String, CacheEntry> loaded = gson.fromJson(json,
                        new TypeToken<Map<String, CacheEntry>>(){}.getType());
                if (loaded != null) {
                    cacheIndex.putAll(loaded);
                }
            } catch (IOException e) {
                logger.warn("Failed to load cache index", e);
            }
        }
    }

    private void saveIndex() {
        if (!indexDirty) {
            return;
        }
        try {
            String indexPath = cachePath + File.separator + INDEX_FILE;
            String json = gson.toJson(cacheIndex);
            Files.write(Paths.get(indexPath), json.getBytes(StandardCharsets.UTF_8));
            indexDirty = false;
            pendingIndexWrites.set(0);
        } catch (IOException e) {
            logger.error("Failed to save cache index", e);
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            File cacheDir = new File(cachePath);
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && !f.getName().equals(INDEX_FILE)) {
                        f.delete();
                    }
                }
            }
            cacheIndex.clear();
            memoryCache.clear();
            hits.reset();
            misses.reset();
            pendingIndexWrites.set(0);
            indexDirty = true;
            saveIndex();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public CacheStatistics getStatistics() {
        lock.readLock().lock();
        try {
            long totalSize = 0;
            File cacheDir = new File(cachePath);
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile()) totalSize += f.length();
                }
            }
            return new CacheStatistics(cacheIndex.size(), totalSize, hits.intValue(), misses.intValue());
        } finally {
            lock.readLock().unlock();
        }
    }

    public String getCachePath() {
        return cachePath;
    }

    public void setCachePath(String newCachePath) {
        lock.writeLock().lock();
        try {
            this.cachePath = newCachePath;
            File cacheDir = new File(cachePath);
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            cacheIndex.clear();
            memoryCache.clear();
            hits.reset();
            misses.reset();
            pendingIndexWrites.set(0);
            indexDirty = false;
            loadIndex();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void close() {
        flushIndex();
    }

    public void flushIndex() {
        lock.writeLock().lock();
        try {
            saveIndex();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void markIndexDirty() {
        indexDirty = true;
        int threshold = readIntProperty(INDEX_FLUSH_THRESHOLD_PROP, DEFAULT_INDEX_FLUSH_THRESHOLD);
        if (pendingIndexWrites.incrementAndGet() >= Math.max(1, threshold)) {
            saveIndex();
        }
    }

    private static int readIntProperty(String name, int defaultValue) {
        String value = System.getProperty(name);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static class CacheEntry {
        public String sourceLang;
        public String targetLang;
        public String fileName;
        public long createdAt;

        public CacheEntry() {}

        public CacheEntry(String sourceLang, String targetLang, String fileName) {
            this.sourceLang = sourceLang;
            this.targetLang = targetLang;
            this.fileName = fileName;
            this.createdAt = System.currentTimeMillis();
        }
    }
}
