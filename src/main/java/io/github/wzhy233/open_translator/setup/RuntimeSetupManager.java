package io.github.wzhy233.open_translator.setup;

import io.github.wzhy233.open_translator.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class RuntimeSetupManager {
    private static final Logger logger = LoggerFactory.getLogger(RuntimeSetupManager.class);
    private static final String[] REQUIRED_MODULES = {
            "ctranslate2", "transformers", "sentencepiece", "huggingface_hub"
    };
    private static final String[] REQUIRED_PAIRS = {"en_zh", "zh_en"};
    private static final String UI_ENABLED_PROP = "open_translator.ui.enabled";
    private static final String PYTHON_PROP = "open_translator.python";
    private static final String MODEL_ROOT_PROP = "open_translator.model_root";

    private RuntimeSetupManager() {
    }

    public static SetupStatus inspect() {
        SetupStatus status = new SetupStatus();
        status.pythonPath = resolvePythonExecutable();
        status.pythonAvailable = canRunPython(status.pythonPath);
        status.modelRoot = resolveModelRoot();
        status.licenseAccepted = ConfigManager.isLicenseAccepted();
        status.licenseSigner = ConfigManager.getLicenseSigner();
        if (status.pythonAvailable) {
            status.dependenciesInstalled = hasRequiredModules(status.pythonPath);
        }
        if (status.modelRoot != null) {
            for (String pair : REQUIRED_PAIRS) {
                Path pairDir = status.modelRoot.resolve(pair);
                if (!(Files.isDirectory(pairDir) && Files.exists(pairDir.resolve("model.bin")))) {
                    status.missingPairs.add(pair);
                }
            }
        }
        return status;
    }

    public static SetupStatus ensureReady(boolean interactive) {
        SetupStatus status = inspect();
        if (status.isReady()) {
            return status;
        }
        if (interactive && isUiEnabled() && !GraphicsEnvironment.isHeadless()) {
            SetupWizard wizard = new SetupWizard(status);
            wizard.showDialog();
            status = inspect();
            if (status.isReady()) {
                return status;
            }
        }
        throw new IllegalStateException(
                "OpenTranslatorLite runtime is blocked: " + status.describeProblems()
                        + "."
        );
    }

    public static void installDependencies(String pythonPath, Consumer<String> loggerConsumer) {
        Path requirements = extractBundledResource("/python/requirements.txt", "requirements", ".txt");
        runCommand(loggerConsumer, Arrays.asList(
                pythonPath, "-m", "pip", "install", "-r", requirements.toString()
        ));
    }

    public static void downloadModels(String pythonPath, Path modelRoot, Consumer<String> loggerConsumer) {
        Path script = extractBundledResource("/python/ctranslate2_model_setup.py", "model-setup", ".py");
        runCommand(loggerConsumer, Arrays.asList(
                pythonPath, script.toString(), "--model-root", modelRoot.toString()
        ));
    }

    public static void acceptLicense(String signerName) {
        ConfigManager.acceptLicense(signerName);
    }

    public static void savePaths(String pythonPath, Path modelRoot) {
        if (pythonPath != null) {
            ConfigManager.setPythonPath(pythonPath);
        }
        if (modelRoot != null) {
            ConfigManager.setModelRoot(modelRoot.toString());
        }
    }

    private static boolean isUiEnabled() {
        return Boolean.parseBoolean(System.getProperty(UI_ENABLED_PROP, "true"));
    }

    private static String resolvePythonExecutable() {
        String configured = System.getProperty(PYTHON_PROP);
        if (configured != null && !configured.trim().isEmpty()) {
            return configured;
        }
        configured = ConfigManager.getPythonPath();
        if (configured != null && !configured.trim().isEmpty()) {
            return configured;
        }
        Path localVenv = Paths.get("scripts", ".venv", "Scripts", "python.exe");
        if (Files.exists(localVenv)) {
            return localVenv.toAbsolutePath().toString();
        }
        return "python";
    }

    private static Path resolveModelRoot() {
        String configured = System.getProperty(MODEL_ROOT_PROP);
        if (configured != null && !configured.trim().isEmpty()) {
            return Paths.get(configured);
        }
        configured = ConfigManager.getModelRoot();
        return Paths.get(configured);
    }

    private static boolean canRunPython(String pythonPath) {
        try {
            Process process = new ProcessBuilder(pythonPath, "--version").start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean hasRequiredModules(String pythonPath) {
        String command = "import " + String.join(",", REQUIRED_MODULES);
        try {
            Process process = new ProcessBuilder(pythonPath, "-c", command).start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static Path extractBundledResource(String resourcePath, String prefix, String suffix) {
        try (InputStream input = RuntimeSetupManager.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("Bundled resource not found: " + resourcePath);
            }
            Path file = Files.createTempFile(prefix, suffix);
            Files.copy(input, file, StandardCopyOption.REPLACE_EXISTING);
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to extract resource: " + resourcePath, e);
        }
    }

    private static void runCommand(Consumer<String> loggerConsumer, List<String> command) {
        Objects.requireNonNull(command, "command");
        try {
            Files.createDirectories(resolveModelRoot());
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (loggerConsumer != null) {
                        loggerConsumer.accept(line);
                    }
                    logger.info("[setup] {}", line);
                }
            }
            if (process.waitFor() != 0) {
                throw new IllegalStateException("Command failed: " + String.join(" ", command));
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Failed to execute setup command: " + String.join(" ", command), e);
        }
    }
}
