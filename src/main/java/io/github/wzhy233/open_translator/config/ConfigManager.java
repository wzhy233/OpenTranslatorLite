package io.github.wzhy233.open_translator.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;


public class ConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);

    private static final String CONFIG_DIR = System.getProperty("user.home")
            + File.separator + ".open_translator"
            + File.separator + "OpenTranslatorLite";

    private static final String CONFIG_FILE = CONFIG_DIR + File.separator + "cache_path.ini";

    private static final String DEFAULT_CACHE_PATH = CONFIG_DIR + File.separator + "cache";

    private static final String CACHE_PATH_KEY = "cache_path";
    private static volatile String cachedCachePath;

    public static void initConfig() {
        try {
            ensureConfigDir();
            if (!new File(CONFIG_FILE).exists()) {
                writeCachePath(DEFAULT_CACHE_PATH);
            }
        } catch (IOException e) {
            logger.error("Failed to initialize config", e);
            throw new RuntimeException("Failed to initialize config", e);
        }
    }

    public static String getCachePath() {
        if (cachedCachePath != null) {
            return cachedCachePath;
        }
        initConfig();
        try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
            Properties props = new Properties();
            props.load(fis);
            cachedCachePath = props.getProperty(CACHE_PATH_KEY, DEFAULT_CACHE_PATH).trim();
            return cachedCachePath;
        } catch (IOException e) {
            logger.warn("Failed to read config file, using default cache path", e);
            return DEFAULT_CACHE_PATH;
        }
    }

    public static void setCachePath(String cachePath) {
        try {
            ensureConfigDir();
        } catch (IOException e) {
            logger.error("Failed to initialize config directory", e);
            throw new RuntimeException("Failed to initialize config", e);
        }
        cachedCachePath = cachePath;
        writeCachePath(cachePath);
    }

    private static void ensureConfigDir() throws IOException {
        Files.createDirectories(Paths.get(CONFIG_DIR));
    }

    private static void writeCachePath(String cachePath) {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            Properties props = new Properties();
            props.setProperty(CACHE_PATH_KEY, cachePath);
            props.store(fos, "OpenTranslatorLite Cache Configuration");
        } catch (IOException e) {
            logger.error("Failed to write config file", e);
            throw new RuntimeException("Failed to write config", e);
        }
    }

    public static String getConfigFilePath() {
        return CONFIG_FILE;
    }
}
