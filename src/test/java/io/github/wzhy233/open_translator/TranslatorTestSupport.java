package io.github.wzhy233.open_translator;

import io.github.wzhy233.open_translator.config.ConfigManager;
import io.github.wzhy233.open_translator.setup.RuntimeSetupManager;

import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class TranslatorTestSupport {
    private static final String TEST_SETUP_UI_PROP = "open_translator.test_setup_ui";
    private String previousUiEnabled;
    private String previousPython;
    private String previousModelRoot;

    void beforeAll() throws Exception {
        previousUiEnabled = System.getProperty("open_translator.ui.enabled");
        previousPython = System.getProperty("open_translator.python");
        previousModelRoot = System.getProperty("open_translator.model_root");

        String python = detectPython();
        Path modelRoot = Paths.get(System.getProperty("user.home"), ".open_translator",
                "OpenTranslatorLite", "models", "ctranslate2");

        ConfigManager.setPythonPath(python);
        ConfigManager.setModelRoot(modelRoot.toString());
        System.setProperty("open_translator.python", python);
        System.setProperty("open_translator.model_root", modelRoot.toString());

        if (shouldUseUiSetup()) {
            System.setProperty("open_translator.ui.enabled", "true");
            RuntimeSetupManager.ensureReady(true);
        } else {
            System.setProperty("open_translator.ui.enabled", "false");
            runCommand(Arrays.asList(python, "-m", "pip", "install", "-r", "scripts/requirements.txt"));
            if (!Files.exists(modelRoot.resolve("en_zh").resolve("model.bin"))
                    || !Files.exists(modelRoot.resolve("zh_en").resolve("model.bin"))) {
                runCommand(Arrays.asList(python, "scripts/init_models.py", "--model-root", modelRoot.toString()));
            }
            ConfigManager.acceptLicense("TranslatorTest");
        }
    }

    void afterAll() {
        restoreProperty("open_translator.ui.enabled", previousUiEnabled);
        restoreProperty("open_translator.python", previousPython);
        restoreProperty("open_translator.model_root", previousModelRoot);
    }

    private static String detectPython() {
        Path localVenv = Paths.get("scripts", ".venv", "Scripts", "python.exe").toAbsolutePath();
        if (Files.exists(localVenv)) {
            return localVenv.toString();
        }
        return "python";
    }

    private static boolean shouldUseUiSetup() {
        String value = System.getProperty(TEST_SETUP_UI_PROP, "auto");
        if ("true".equalsIgnoreCase(value)) {
            return !GraphicsEnvironment.isHeadless();
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        return !GraphicsEnvironment.isHeadless();
    }

    private static void runCommand(List<String> command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        List<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.add(line);
            }
        }
        if (process.waitFor() != 0) {
            throw new IllegalStateException(String.join(System.lineSeparator(), output));
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
