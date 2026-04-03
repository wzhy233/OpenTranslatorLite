package io.github.wzhy233.open_translator.model;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.github.wzhy233.open_translator.BuildInfo;
import io.github.wzhy233.open_translator.setup.RuntimeSetupManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

public class TranslationModel implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(TranslationModel.class);
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();
    private static final String PYTHON_PROP = "open_translator.python";
    private static final String PYTHON_ENV = "OPEN_TRANSLATOR_PYTHON";
    private static final String WORKER_PROP = "open_translator.worker_script";
    private static final String WORKER_ENV = "OPEN_TRANSLATOR_WORKER_SCRIPT";
    private static final String COMPUTE_TYPE_PROP = "open_translator.compute_type";
    private static final String COMPUTE_TYPE_ENV = "OPEN_TRANSLATOR_COMPUTE_TYPE";
    private static final String DEVICE_PROP = "open_translator.device";
    private static final String DEVICE_ENV = "OPEN_TRANSLATOR_DEVICE";
    private static final String INTRA_THREADS_PROP = "open_translator.intra_threads";
    private static final String WORKER_COUNT_PROP = "open_translator.worker_count";
    private static final String MAX_BATCH_SIZE_PROP = "open_translator.max_batch_size";
    private static final int DEFAULT_INTRA_THREADS = 4;
    private static final int DEFAULT_MAX_BATCH_SIZE = 8;

    private final Set<String> supportedPairs = ConcurrentHashMap.newKeySet();
    private final List<WorkerSession> workers = new ArrayList<>();
    private final AtomicInteger nextWorkerIndex = new AtomicInteger();
    private Path extractedWorkerScript;

    public void initialize() {
        try {
            RuntimeSetupManager.ensureReady(!BuildInfo.isSilentBuild());
            startWorkers();
            List<String> pairs = fetchSupportedPairs();
            supportedPairs.clear();
            supportedPairs.addAll(pairs);
        } catch (Exception e) {
            close();
            throw new RuntimeException("Failed to initialize translation models", e);
        }
    }

    public Set<String> getSupportedPairs() {
        return Collections.unmodifiableSet(supportedPairs);
    }

    public String translate(String sourceLang, String targetLang, String content) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("cmd", "translate");
        request.put("pair", sourceLang + "_" + targetLang);
        request.put("text", content);
        Map<String, Object> response = borrowWorker().sendRequest(request);
        Object translated = response.get("text");
        return translated == null ? "" : translated.toString();
    }

    public String[] translateBatch(String sourceLang, String targetLang, String[] contents) {
        if (contents == null || contents.length == 0) {
            return new String[0];
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("cmd", "translate_batch");
        request.put("pair", sourceLang + "_" + targetLang);
        request.put("texts", contents);
        request.put("max_batch_size", readIntProp(MAX_BATCH_SIZE_PROP, DEFAULT_MAX_BATCH_SIZE));
        Map<String, Object> response = borrowWorker().sendRequest(request);
        Object texts = response.get("texts");
        if (!(texts instanceof List<?>)) {
            throw new IllegalStateException("Translation worker returned invalid batch response");
        }
        List<?> rawTexts = (List<?>) texts;
        String[] translated = new String[rawTexts.size()];
        for (int i = 0; i < rawTexts.size(); i++) {
            Object value = rawTexts.get(i);
            translated[i] = value == null ? "" : value.toString();
        }
        return translated;
    }

    @Override
    public void close() {
        for (WorkerSession worker : workers) {
            worker.close();
        }
        workers.clear();

        if (extractedWorkerScript != null) {
            try {
                Files.deleteIfExists(extractedWorkerScript);
            } catch (IOException ignored) {
            }
            extractedWorkerScript = null;
        }
    }

    public static int defaultWorkerCount() {
        int cpuCount = Runtime.getRuntime().availableProcessors();
        int recommended = Math.max(1, Math.min(4, cpuCount / 2));
        return readIntProp(WORKER_COUNT_PROP, recommended);
    }

    private void startWorkers() throws IOException {
        for (int i = 0; i < defaultWorkerCount(); i++) {
            workers.add(startWorker(i));
        }
    }

    private WorkerSession startWorker(int index) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(resolvePythonExecutable());
        command.add("-u");
        command.add(resolveWorkerScript().toString());
        command.add("--model-root");
        command.add(RuntimeSetupManager.inspect().modelRoot.toString());
        command.add("--device");
        command.add(readSetting(DEVICE_PROP, DEVICE_ENV, "cpu"));
        command.add("--compute-type");
        command.add(readSetting(COMPUTE_TYPE_PROP, COMPUTE_TYPE_ENV, "int8"));
        command.add("--intra-threads");
        command.add(String.valueOf(readIntProp(INTRA_THREADS_PROP, DEFAULT_INTRA_THREADS)));

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(false);
        builder.environment().put("PYTHONIOENCODING", "UTF-8");
        builder.environment().put("PYTHONUTF8", "1");
        Process workerProcess = builder.start();
        BufferedWriter workerInput = new BufferedWriter(new OutputStreamWriter(workerProcess.getOutputStream(), StandardCharsets.UTF_8));
        BufferedReader workerOutput = new BufferedReader(new InputStreamReader(workerProcess.getInputStream(), StandardCharsets.UTF_8));
        Thread stderrThread = new Thread(() -> streamWorkerStderr(workerProcess), "ctranslate2-worker-stderr-" + index);
        stderrThread.setDaemon(true);
        stderrThread.start();
        return new WorkerSession(workerProcess, workerInput, workerOutput, stderrThread);
    }

    private List<String> fetchSupportedPairs() {
        Map<String, Object> request = new HashMap<>();
        request.put("cmd", "list_supported_pairs");
        Map<String, Object> response = borrowWorker().sendRequest(request);
        Object pairsObj = response.get("pairs");
        if (!(pairsObj instanceof List<?>)) {
            return Collections.emptyList();
        }
        List<?> rawPairs = (List<?>) pairsObj;
        List<String> pairs = new ArrayList<>(rawPairs.size());
        for (Object rawPair : rawPairs) {
            if (rawPair != null) {
                pairs.add(rawPair.toString());
            }
        }
        return pairs;
    }

    private void streamWorkerStderr(Process process) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.info("[ct2-worker] {}", line);
            }
        } catch (IOException e) {
            logger.debug("Translation worker stderr closed", e);
        }
    }

    private WorkerSession borrowWorker() {
        if (workers.isEmpty()) {
            throw new IllegalStateException("Translation worker pool is empty");
        }
        int index = Math.abs(nextWorkerIndex.getAndIncrement());
        WorkerSession worker = workers.get(index % workers.size());
        worker.ensureWorkerRunning();
        return worker;
    }

    private Path resolveWorkerScript() throws IOException {
        String configured = readSetting(WORKER_PROP, WORKER_ENV, null);
        if (configured != null && !configured.trim().isEmpty()) {
            return Paths.get(configured);
        }

        if (extractedWorkerScript != null && Files.exists(extractedWorkerScript)) {
            return extractedWorkerScript;
        }

        try (InputStream input = TranslationModel.class.getResourceAsStream("/python/ctranslate2_worker.py")) {
            if (input == null) {
                throw new IllegalStateException("Bundled worker script not found");
            }
            extractedWorkerScript = Files.createTempFile("open-translator-ctranslate2-worker", ".py");
            Files.copy(input, extractedWorkerScript, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            extractedWorkerScript.toFile().deleteOnExit();
            return extractedWorkerScript;
        }
    }

    private String resolvePythonExecutable() {
        String configured = readSetting(PYTHON_PROP, PYTHON_ENV, null);
        if (configured != null && !configured.trim().isEmpty()) {
            return configured;
        }
        return RuntimeSetupManager.inspect().pythonPath;
    }

    private static String readSetting(String propName, String envName, String defaultValue) {
        String propValue = System.getProperty(propName);
        if (propValue != null && !propValue.trim().isEmpty()) {
            return propValue;
        }
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.trim().isEmpty()) {
            return envValue;
        }
        return defaultValue;
    }

    private static int readIntProp(String name, int defaultValue) {
        String value = System.getProperty(name);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static final class WorkerSession {
        private final Object ioLock = new Object();
        private final Process workerProcess;
        private final BufferedWriter workerInput;
        private final BufferedReader workerOutput;
        private final Thread stderrThread;

        private WorkerSession(Process workerProcess, BufferedWriter workerInput,
                              BufferedReader workerOutput, Thread stderrThread) {
            this.workerProcess = workerProcess;
            this.workerInput = workerInput;
            this.workerOutput = workerOutput;
            this.stderrThread = stderrThread;
        }

        private Map<String, Object> sendRequest(Map<String, Object> request) {
            synchronized (ioLock) {
                ensureWorkerRunning();
                try {
                    workerInput.write(GSON.toJson(request));
                    workerInput.newLine();
                    workerInput.flush();

                    String line = workerOutput.readLine();
                    if (line == null) {
                        throw new IllegalStateException("Translation worker terminated unexpectedly");
                    }

                    Map<String, Object> response = GSON.fromJson(line, MAP_TYPE);
                    if (response == null) {
                        throw new IllegalStateException("Translation worker returned empty response");
                    }
                    Object ok = response.get("ok");
                    if (!(ok instanceof Boolean) || !((Boolean) ok)) {
                        Object message = response.get("error");
                        throw new IllegalStateException(message == null ? "Translation worker failed" : message.toString());
                    }
                    return response;
                } catch (IOException e) {
                    throw new RuntimeException("Failed to communicate with translation worker", e);
                }
            }
        }

        private void ensureWorkerRunning() {
            if (workerProcess == null || !workerProcess.isAlive()) {
                throw new IllegalStateException("Translation worker is not running");
            }
        }

        private void close() {
            try {
                Map<String, Object> request = new HashMap<>();
                request.put("cmd", "shutdown");
                sendRequest(request);
            } catch (Exception ignored) {
            }
            workerProcess.destroy();
            try {
                workerProcess.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            stderrThread.interrupt();
        }
    }
}
