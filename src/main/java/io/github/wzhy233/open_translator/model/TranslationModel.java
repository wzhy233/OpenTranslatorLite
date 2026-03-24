package io.github.wzhy233.open_translator.model;

import ai.djl.sentencepiece.SpTokenizer;
import ai.onnxruntime.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TranslationModel {
    private static final Logger logger = LoggerFactory.getLogger(TranslationModel.class);

    private static final String[] CANDIDATE_PAIRS = {
            "en_zh", "zh_en"
    };
    private static final String SKIP_MODEL_LOAD_PROP = "open_translator.skip_model_load";
    private static final int EOS_TOKEN_ID = 0;
    private static final int DEFAULT_MAX_LENGTH = 128;
    private static final int DEFAULT_MAX_REPEAT = 3;

    private final Map<String, Seq2SeqModel> modelCache = new ConcurrentHashMap<>();
    private final Map<String, SentencePieceTokenizer> vocabCache = new ConcurrentHashMap<>();
    private final Map<String, SentencePieceTokenizer> tokenizerCache = new ConcurrentHashMap<>();
    private final Set<String> supportedPairs = new HashSet<>();
    private OrtEnvironment ortEnv;
    private int maxLength = DEFAULT_MAX_LENGTH;
    private int maxRepeat = DEFAULT_MAX_REPEAT;

    public void initialize() {
        try {
            boolean skipModelLoad = Boolean.parseBoolean(System.getProperty(SKIP_MODEL_LOAD_PROP, "false"));
            maxLength = readIntProp("open_translator.max_length", DEFAULT_MAX_LENGTH);
            maxRepeat = readIntProp("open_translator.max_repeat", DEFAULT_MAX_REPEAT);
            if (!skipModelLoad) {
                ortEnv = OrtEnvironment.getEnvironment();
            }

            for (String pair : CANDIDATE_PAIRS) {
                String[] parts = pair.split("_", 2);
                if (parts.length != 2) {
                    continue;
                }
                String source = parts[0];
                String target = parts[1];
                if (hasModelResources(source, target)) {
                    initializeLanguagePair(source, target, !skipModelLoad);
                    supportedPairs.add(source + "-" + target);
                } else {
                    logger.warn("Missing model resources for {} -> {}", source, target);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to initialize translation models", e);
            throw new RuntimeException("Failed to initialize models", e);
        }
    }

    private void initializeLanguagePair(String source, String target, boolean loadModels) throws OrtException {
        String pair = source + "_" + target;

        SentencePieceTokenizer sourceTokenizer = new SentencePieceTokenizer(pair, "source");
        SentencePieceTokenizer targetTokenizer = new SentencePieceTokenizer(pair, "target");
        tokenizerCache.put(pair, sourceTokenizer);
        vocabCache.put(pair, targetTokenizer);

        Seq2SeqModel model = new Seq2SeqModel(source, target, ortEnv, loadModels);
        modelCache.put(pair, model);
    }

    public Set<String> getSupportedPairs() {
        return Collections.unmodifiableSet(supportedPairs);
    }

    private boolean hasModelResources(String source, String target) {
        String encoderPath = "/models/pretrained/" + source + "_" + target + "_encoder.onnx";
        String decoderPath = "/models/pretrained/" + source + "_" + target + "_decoder.onnx";
        String sourceSpm = "/models/pretrained/" + source + "_" + target + "_source.spm";
        String targetSpm = "/models/pretrained/" + source + "_" + target + "_target.spm";
        return resourceExists(encoderPath) && resourceExists(decoderPath)
                && resourceExists(sourceSpm) && resourceExists(targetSpm);
    }

    private boolean resourceExists(String resourcePath) {
        try (InputStream input = TranslationModel.class.getResourceAsStream(resourcePath)) {
            return input != null;
        } catch (IOException e) {
            return false;
        }
    }

    private static byte[] readResourceBytes(String resourcePath) {
        try (InputStream input = TranslationModel.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("Model resource not found: " + resourcePath);
            }
            return input.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read model resource: " + resourcePath, e);
        }
    }

    public String translate(String sourceLang, String targetLang, String content) throws OrtException {
        String pair = sourceLang + "_" + targetLang;
        Seq2SeqModel model = modelCache.get(pair);
        SentencePieceTokenizer tokenizer = tokenizerCache.get(pair);
        SentencePieceTokenizer targetVocab = vocabCache.get(pair);

        if (model == null || tokenizer == null || targetVocab == null) {
            throw new RuntimeException("Model not found: " + pair);
        }

        List<Integer> inputTokens = tokenizer.encodeWithEos(content);
        int eosTokenId = targetVocab.getEosId();
        int decoderStartId = targetVocab.getPadId();
        List<Integer> outputTokens = model.generateTranslation(inputTokens, decoderStartId, eosTokenId, maxLength, maxRepeat);
        if (Boolean.parseBoolean(System.getProperty("open_translator.debug_ids", "false"))) {
            logger.info("Output token ids: {}", outputTokens);
        }
        return targetVocab.decode(outputTokens);
    }

    public static class SentencePieceTokenizer implements AutoCloseable {
        private static final String EOS_TOKEN = "</s>";
        private static final String PAD_TOKEN = "<pad>";
        private static final String UNK_TOKEN = "<unk>";
        private static final Map<String, List<String>> TARGET_PREFIXES = Map.of(
                "en_zh", Arrays.asList(">>cmn_Hans<<", ">>cmn<<")
        );
        private static final Gson GSON = new Gson();
        private final SpTokenizer tokenizer;
        private final Map<String, Integer> tokenToId;
        private final Map<Integer, String> idToToken;
        private final int eosId;
        private final int padId;
        private final int unkId;
        private final Integer prefixId;

        public SentencePieceTokenizer(String pair, String role) {
            String modelPath = "/models/pretrained/" + pair + "_" + role + ".spm";
            String vocabPath = "/models/pretrained/" + pair + "_vocab.json";
            try (InputStream input = TranslationModel.class.getResourceAsStream(modelPath)) {
                if (input == null) {
                    throw new IllegalStateException("Model resource not found: " + modelPath);
                }
                this.tokenizer = new SpTokenizer(input);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load model: " + modelPath, e);
            }
            this.tokenToId = loadVocab(vocabPath);
            this.idToToken = buildReverseVocab(tokenToId);
            this.eosId = tokenToId.getOrDefault(EOS_TOKEN, EOS_TOKEN_ID);
            this.padId = tokenToId.getOrDefault(PAD_TOKEN, 65000);
            this.unkId = tokenToId.getOrDefault(UNK_TOKEN, 1);
            if ("source".equals(role)) {
                this.prefixId = resolvePrefixId(pair);
            } else {
                this.prefixId = null;
            }
        }

        private Integer resolvePrefixId(String pair) {
            List<String> candidates = TARGET_PREFIXES.get(pair);
            if (candidates == null) {
                return null;
            }
            for (String token : candidates) {
                Integer id = tokenToId.get(token);
                if (id != null) {
                    return id;
                }
            }
            return null;
        }

        private static Map<String, Integer> loadVocab(String vocabPath) {
            try (InputStream input = TranslationModel.class.getResourceAsStream(vocabPath)) {
                if (input == null) {
                    throw new IllegalStateException("Model resource not found: " + vocabPath);
                }
                String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                Map<String, Double> raw = GSON.fromJson(json, new TypeToken<Map<String, Double>>() {}.getType());
                Map<String, Integer> vocab = new HashMap<>();
                for (Map.Entry<String, Double> entry : raw.entrySet()) {
                    vocab.put(entry.getKey(), entry.getValue().intValue());
                }
                return vocab;
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load vocab: " + vocabPath, e);
            }
        }

        private static Map<Integer, String> buildReverseVocab(Map<String, Integer> tokenToId) {
            Map<Integer, String> reverse = new HashMap<>();
            for (Map.Entry<String, Integer> entry : tokenToId.entrySet()) {
                reverse.put(entry.getValue(), entry.getKey());
            }
            return reverse;
        }

        public List<Integer> encodeWithEos(String text) {
            List<String> pieces = tokenizer.tokenize(text);
            int extra = prefixId == null ? 1 : 2;
            List<Integer> tokens = new ArrayList<>(pieces.size() + extra);
            if (prefixId != null) {
                tokens.add(prefixId);
            }
            for (String piece : pieces) {
                tokens.add(tokenToId.getOrDefault(piece, unkId));
            }
            tokens.add(eosId);
            return tokens;
        }

        public String decode(List<Integer> tokenIds) {
            List<String> pieces = new ArrayList<>(tokenIds.size());
            for (Integer id : tokenIds) {
                if (id == null || id == eosId || id == padId) {
                    continue;
                }
                String piece = idToToken.get(id);
                if (piece == null || piece.startsWith(">>") || piece.startsWith("<")) {
                    continue;
                }
                pieces.add(piece);
            }
            return pieces.isEmpty() ? "" : tokenizer.buildSentence(pieces).trim();
        }

        public int getEosId() {
            return eosId;
        }

        public int getPadId() {
            return padId;
        }

        @Override
        public void close() {
            tokenizer.close();
        }
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

    public static class Seq2SeqModel {
        private final String source;
        private final String target;
        private final OrtEnvironment ortEnv;
        private final OrtSession encoderSession;
        private final OrtSession decoderSession;
        private final String encoderInputIdsName;
        private final String encoderAttentionName;
        private final String decoderInputIdsName;
        private final String decoderEncoderHiddenName;
        private final String decoderEncoderAttentionName;
        private final String decoderAttentionName;
        private final String decoderLogitsName;

        public Seq2SeqModel(String source, String target, OrtEnvironment ortEnv, boolean loadModels) throws OrtException {
            this.source = source;
            this.target = target;
            this.ortEnv = ortEnv;

            String encoderPath = "/models/pretrained/" + source + "_" + target + "_encoder.onnx";
            String decoderPath = "/models/pretrained/" + source + "_" + target + "_decoder.onnx";

            try {
                if (loadModels) {
                    OrtSession.SessionOptions options = new OrtSession.SessionOptions();
                    byte[] encoderBytes = readResourceBytes(encoderPath);
                    byte[] decoderBytes = readResourceBytes(decoderPath);
                    this.encoderSession = ortEnv.createSession(encoderBytes, options);
                    this.decoderSession = ortEnv.createSession(decoderBytes, options);
                    this.encoderInputIdsName = findInputName(encoderSession, "input_ids");
                    this.encoderAttentionName = findInputName(encoderSession, "attention_mask");
                    this.decoderInputIdsName = findInputName(decoderSession, "input_ids");
                    this.decoderEncoderHiddenName = findInputName(decoderSession, "encoder_hidden_states");
                    this.decoderEncoderAttentionName = findInputName(decoderSession, "encoder_attention_mask");
                    this.decoderAttentionName = findDecoderAttentionName(decoderSession);
                    this.decoderLogitsName = findOutputName(decoderSession, "logits");
                } else {
                    this.encoderSession = null;
                    this.decoderSession = null;
                    this.encoderInputIdsName = null;
                    this.encoderAttentionName = null;
                    this.decoderInputIdsName = null;
                    this.decoderEncoderHiddenName = null;
                    this.decoderEncoderAttentionName = null;
                    this.decoderAttentionName = null;
                    this.decoderLogitsName = null;
                }
            } catch (OrtException e) {
                throw new OrtException(e.getCode(), "Failed to load models: " + source + "_" + target);
            }
        }

        private static byte[] readResourceBytes(String resourcePath) throws OrtException {
            try (InputStream input = TranslationModel.class.getResourceAsStream(resourcePath)) {
                if (input == null) {
                    throw new OrtException(OrtException.OrtErrorCode.ORT_NO_SUCHFILE,
                            "Model resource not found: " + resourcePath);
                }
                return input.readAllBytes();
            } catch (IOException e) {
                throw new OrtException(OrtException.OrtErrorCode.ORT_FAIL,
                        "Failed to read model resource: " + resourcePath);
            }
        }

        public List<Integer> generateTranslation(List<Integer> inputTokens, int decoderStartId, int eosTokenId, int maxLength, int maxRepeat) {
            if (encoderSession == null || decoderSession == null) {
                return Collections.emptyList();
            }
            try {
                long[] inputIds = toLongArray(inputTokens);
                long[][] inputIdsBatch = new long[][] { inputIds };
                long[][] attentionMaskBatch = new long[][] { buildAttentionMask(inputIds.length) };

                try (OnnxTensor inputIdsTensor = OnnxTensor.createTensor(ortEnv, inputIdsBatch);
                     OnnxTensor attentionTensor = OnnxTensor.createTensor(ortEnv, attentionMaskBatch)) {

                    Map<String, OnnxTensor> encoderInputs = new HashMap<>();
                    encoderInputs.put(encoderInputIdsName, inputIdsTensor);
                    if (encoderAttentionName != null) {
                        encoderInputs.put(encoderAttentionName, attentionTensor);
                    }
                    try (OrtSession.Result encoderResult = encoderSession.run(encoderInputs)) {
                        OnnxTensor encoderHidden = (OnnxTensor) encoderResult.get(0);
                        List<Integer> decoded = new ArrayList<>();
                        decoded.add(decoderStartId);
                        int lastToken = -1;
                        int repeatCount = 0;
                        for (int step = 0; step < maxLength; step++) {
                            long[] decoderIds = toLongArray(decoded);
                            long[][] decoderIdsBatch = new long[][] { decoderIds };
                            long[][] decoderAttentionBatch = new long[][] { buildAttentionMask(decoderIds.length) };
                            try (OnnxTensor decoderInput = OnnxTensor.createTensor(ortEnv, decoderIdsBatch);
                                 OnnxTensor encoderAttention = OnnxTensor.createTensor(ortEnv, attentionMaskBatch);
                                 OnnxTensor decoderAttention = OnnxTensor.createTensor(ortEnv, decoderAttentionBatch)) {
                                Map<String, OnnxTensor> decoderInputs = new HashMap<>();
                                decoderInputs.put(decoderInputIdsName, decoderInput);
                                decoderInputs.put(decoderEncoderHiddenName, encoderHidden);
                                if (decoderEncoderAttentionName != null) {
                                    decoderInputs.put(decoderEncoderAttentionName, encoderAttention);
                                }
                                if (decoderAttentionName != null) {
                                    decoderInputs.put(decoderAttentionName, decoderAttention);
                                }
                                try (OrtSession.Result decoderResult = decoderSession.run(decoderInputs)) {
                                    OnnxValue logitsValue = decoderResult.get(decoderLogitsName).orElseThrow();
                                    float[][][] logits = (float[][][]) logitsValue.getValue();
                                    int nextId = argMax(logits[0][logits[0].length - 1]);
                                    if (nextId == eosTokenId) {
                                        break;
                                    }
                                    if (nextId == lastToken) {
                                        repeatCount++;
                                        if (repeatCount >= maxRepeat) {
                                            break;
                                        }
                                    } else {
                                        repeatCount = 0;
                                        lastToken = nextId;
                                    }
                                    decoded.add(nextId);
                                }
                            }
                        }
                        if (!decoded.isEmpty() && (decoded.get(0) == eosTokenId || decoded.get(0) == decoderStartId)) {
                            decoded.remove(0);
                        }
                        return decoded;
                    }
                }
            } catch (OrtException e) {
                throw new RuntimeException("Translation failed", e);
            }
        }

        private static String findInputName(OrtSession session, String contains) {
            for (String name : session.getInputNames()) {
                if (name.contains(contains)) {
                    return name;
                }
            }
            return session.getInputNames().iterator().next();
        }

        private static String findDecoderAttentionName(OrtSession session) {
            for (String name : session.getInputNames()) {
                if (name.contains("attention_mask") && !name.contains("encoder")) {
                    return name;
                }
            }
            return null;
        }

        private static String findOutputName(OrtSession session, String contains) {
            for (String name : session.getOutputNames()) {
                if (name.contains(contains)) {
                    return name;
                }
            }
            return session.getOutputNames().iterator().next();
        }

        private static long[] toLongArray(List<Integer> values) {
            long[] out = new long[values.size()];
            for (int i = 0; i < values.size(); i++) {
                out[i] = values.get(i);
            }
            return out;
        }

        private static long[] buildAttentionMask(int length) {
            long[] mask = new long[length];
            Arrays.fill(mask, 1L);
            return mask;
        }

        private static int argMax(float[] values) {
            int best = 0;
            float max = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < values.length; i++) {
                if (values[i] > max) {
                    max = values[i];
                    best = i;
                }
            }
            return best;
        }
    }
}
