package io.github.wzhy233.open_translator.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;

public class ConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
    private static final String APP_SECRET = "OpenTranslatorLite-License-Seal-v1";
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;

    private static final String CONFIG_DIR = System.getProperty("user.home")
            + java.io.File.separator + ".open_translator"
            + java.io.File.separator + "OpenTranslatorLite";
    private static final String CONFIG_FILE = CONFIG_DIR + java.io.File.separator + "runtime.properties";
    private static final String DEFAULT_CACHE_PATH = CONFIG_DIR + java.io.File.separator + "cache";
    private static final String DEFAULT_MODEL_ROOT = CONFIG_DIR + java.io.File.separator + "models"
            + java.io.File.separator + "ctranslate2";

    private static final String CACHE_PATH_KEY = "cache_path";
    private static final String PYTHON_PATH_KEY = "python_path";
    private static final String MODEL_ROOT_KEY = "model_root";
    private static final String UI_THEME_KEY = "ui_theme";
    private static final String LICENSE_BLOB_KEY = "license_blob";

    private static volatile Properties cachedProperties;

    public static void initConfig() {
        try {
            ensureConfigDir();
            Path configPath = Path.of(CONFIG_FILE);
            if (!Files.exists(configPath)) {
                Properties props = defaultProperties();
                storeProperties(props);
            }
        } catch (IOException e) {
            logger.error("Failed to initialize config", e);
            throw new RuntimeException("Failed to initialize config", e);
        }
    }

    public static String getCachePath() {
        return getProperties().getProperty(CACHE_PATH_KEY, DEFAULT_CACHE_PATH).trim();
    }

    public static void setCachePath(String cachePath) {
        updateProperty(CACHE_PATH_KEY, cachePath);
    }

    public static String getPythonPath() {
        return getProperties().getProperty(PYTHON_PATH_KEY, "").trim();
    }

    public static void setPythonPath(String pythonPath) {
        updateProperty(PYTHON_PATH_KEY, pythonPath == null ? "" : pythonPath.trim());
    }

    public static String getModelRoot() {
        return getProperties().getProperty(MODEL_ROOT_KEY, DEFAULT_MODEL_ROOT).trim();
    }

    public static void setModelRoot(String modelRoot) {
        updateProperty(MODEL_ROOT_KEY, modelRoot == null ? DEFAULT_MODEL_ROOT : modelRoot.trim());
    }

    public static String getUiTheme() {
        return getProperties().getProperty(UI_THEME_KEY, "dark").trim();
    }

    public static void setUiTheme(String theme) {
        updateProperty(UI_THEME_KEY, (theme == null || theme.isBlank()) ? "dark" : theme.trim());
    }

    public static boolean isLicenseAccepted() {
        LicenseRecord record = readLicenseRecord();
        return record != null && currentDeviceFingerprint().equals(record.deviceFingerprint);
    }

    public static void acceptLicense(String signerName) {
        LicenseRecord record = new LicenseRecord(
                signerName == null ? "" : signerName.trim(),
                System.currentTimeMillis(),
                currentDeviceFingerprint()
        );
        updateProperty(LICENSE_BLOB_KEY, encryptLicenseRecord(record));
    }

    public static String getLicenseSigner() {
        LicenseRecord record = readLicenseRecord();
        return record == null ? "" : record.signer;
    }

    public static String getSignedDeviceFingerprint() {
        LicenseRecord record = readLicenseRecord();
        return record == null ? "" : record.deviceFingerprint;
    }

    public static long getLicenseSignedAt() {
        LicenseRecord record = readLicenseRecord();
        return record == null ? 0L : record.signedAt;
    }

    public static String currentDeviceFingerprint() {
        String raw = String.join("|",
                safeHostName(),
                System.getProperty("os.name", ""),
                System.getProperty("os.arch", ""),
                System.getenv().getOrDefault("COMPUTERNAME", ""),
                System.getenv().getOrDefault("HOSTNAME", "")
        );
        return sha256Hex(raw);
    }

    public static String getConfigFilePath() {
        return CONFIG_FILE;
    }

    private static LicenseRecord readLicenseRecord() {
        String blob = getProperties().getProperty(LICENSE_BLOB_KEY, "").trim();
        if (blob.isEmpty()) {
            return null;
        }
        try {
            return decryptLicenseRecord(blob);
        } catch (Exception e) {
            logger.warn("License blob validation failed, treating license as unsigned", e);
            return null;
        }
    }

    private static String encryptLicenseRecord(LicenseRecord record) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, buildLicenseKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(record.serialize().getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt license record", e);
        }
    }

    private static LicenseRecord decryptLicenseRecord(String blob) {
        try {
            byte[] encrypted = Base64.getDecoder().decode(blob);
            ByteBuffer buffer = ByteBuffer.wrap(encrypted);
            byte[] iv = new byte[GCM_IV_BYTES];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, buildLicenseKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            String payload = new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
            return LicenseRecord.deserialize(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt license record", e);
        }
    }

    private static SecretKeySpec buildLicenseKey() {
        String material = APP_SECRET + "|" + currentDeviceFingerprint();
        byte[] digest = sha256(material);
        byte[] key = new byte[16];
        System.arraycopy(digest, 0, key, 0, key.length);
        return new SecretKeySpec(key, "AES");
    }

    private static Properties getProperties() {
        Properties props = cachedProperties;
        if (props != null) {
            return props;
        }
        synchronized (ConfigManager.class) {
            if (cachedProperties == null) {
                initConfig();
                cachedProperties = loadProperties();
            }
            return cachedProperties;
        }
    }

    private static Properties loadProperties() {
        Properties props = defaultProperties();
        try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
            props.load(fis);
        } catch (IOException e) {
            logger.warn("Failed to read config file, using defaults", e);
        }
        return props;
    }

    private static Properties defaultProperties() {
        Properties props = new Properties();
        props.setProperty(CACHE_PATH_KEY, DEFAULT_CACHE_PATH);
        props.setProperty(PYTHON_PATH_KEY, "");
        props.setProperty(MODEL_ROOT_KEY, DEFAULT_MODEL_ROOT);
        props.setProperty(UI_THEME_KEY, "dark");
        props.setProperty(LICENSE_BLOB_KEY, "");
        return props;
    }

    private static String safeHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "";
        }
    }

    private static void updateProperty(String key, String value) {
        Properties props = copyProperties();
        props.setProperty(key, value == null ? "" : value);
        saveProperties(props);
    }

    private static Properties copyProperties() {
        Properties copy = new Properties();
        copy.putAll(getProperties());
        return copy;
    }

    private static void saveProperties(Properties props) {
        try {
            ensureConfigDir();
            storeProperties(props);
            cachedProperties = props;
        } catch (IOException e) {
            logger.error("Failed to write config file", e);
            throw new RuntimeException("Failed to write config", e);
        }
    }

    private static void ensureConfigDir() throws IOException {
        Files.createDirectories(Path.of(CONFIG_DIR));
    }

    private static void storeProperties(Properties props) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "OpenTranslatorLite Runtime Configuration");
        }
    }

    private static byte[] sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String sha256Hex(String text) {
        byte[] bytes = sha256(text);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xff);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    private static final class LicenseRecord {
        private final String signer;
        private final long signedAt;
        private final String deviceFingerprint;

        private LicenseRecord(String signer, long signedAt, String deviceFingerprint) {
            this.signer = signer;
            this.signedAt = signedAt;
            this.deviceFingerprint = deviceFingerprint;
        }

        private String serialize() {
            return signer.replace("\n", " ") + "\n" + signedAt + "\n" + deviceFingerprint;
        }

        private static LicenseRecord deserialize(String payload) {
            String[] parts = payload.split("\n", 3);
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid license payload");
            }
            return new LicenseRecord(parts[0], Long.parseLong(parts[1]), parts[2]);
        }
    }
}
