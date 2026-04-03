package io.github.wzhy233.open_translator;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class BuildInfo {
    private static final String BUILD_VARIANT = "open_translator.build.variant";
    private static final String VERSION = "open_translator.version";
    private static final String GIT_BRANCH = "open_translator.git.branch";
    private static final String GIT_REMOTE_BRANCH = "open_translator.git.remote_branch";
    private static final String GIT_COMMIT = "open_translator.git.commit";
    private static final Properties PROPERTIES = loadProperties();

    private BuildInfo() {
    }

    public static boolean isSilentBuild() {
        return "silent".equalsIgnoreCase(PROPERTIES.getProperty(BUILD_VARIANT, "full"));
    }

    public static String getVersion() {
        return readProperty(VERSION, "unknown");
    }

    public static String getBuildInfo() {
        String branch = readProperty(GIT_BRANCH, readProperty(GIT_REMOTE_BRANCH, "unknown"));
        String commit = readProperty(GIT_COMMIT, "unknown");
        return "(" + branch + "/" + commit + ")";
    }

    public static String getBannerLine() {
        return "                         Version: " + getVersion() + " " + getBuildInfo() + "\n";
    }

    private static String readProperty(String key, String fallback) {
        String value = PROPERTIES.getProperty(key);
        if (value == null) {
            return fallback;
        }
        value = value.trim();
        if (value.isEmpty() || value.startsWith("${")) {
            return fallback;
        }
        return value;
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream input = BuildInfo.class.getResourceAsStream("/open-translator-build.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException ignored) {
        }
        return properties;
    }
}
