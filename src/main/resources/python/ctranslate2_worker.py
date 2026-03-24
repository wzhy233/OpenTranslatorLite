import argparse
import json
import sys
from pathlib import Path

import ctranslate2
import sentencepiece as spm


TARGET_PREFIXES = {
    "en_zh": ">>cmn_Hans<<",
}


def configure_stdio():
    if hasattr(sys.stdin, "reconfigure"):
        sys.stdin.reconfigure(encoding="utf-8")
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8")


class PairTokenizer:
    def __init__(self, model_dir: Path):
        self.source_sp = spm.SentencePieceProcessor(model_file=str(model_dir / "source.spm"))
        self.target_sp = spm.SentencePieceProcessor(model_file=str(model_dir / "target.spm"))
        vocab_file = model_dir / "vocab.json"
        if vocab_file.exists():
            raw_vocab = json.loads(vocab_file.read_text(encoding="utf-8"))
            self.token_to_id = {token: int(index) for token, index in raw_vocab.items()}
        else:
            raw_vocab = json.loads((model_dir / "shared_vocabulary.json").read_text(encoding="utf-8"))
            self.token_to_id = {token: index for index, token in enumerate(raw_vocab)}
        self.id_to_token = {index: token for token, index in self.token_to_id.items()}
        self.eos_id = self.token_to_id.get("</s>", 0)

    def encode(self, pair: str, text: str):
        pieces = list(self.source_sp.EncodeAsPieces(text))
        prefix = TARGET_PREFIXES.get(pair)
        tokens = []
        if prefix and prefix in self.token_to_id:
            tokens.append(prefix)
        tokens.extend(pieces)
        tokens.append("</s>")
        return tokens

    def decode(self, token_pieces):
        cleaned = []
        for piece in token_pieces:
            if piece in {"</s>", "<pad>"}:
                continue
            if piece.startswith(">>") or piece.startswith("<"):
                continue
            cleaned.append(piece)
        if not cleaned:
            return ""
        return self.target_sp.DecodePieces(cleaned).strip()


class Worker:
    def __init__(self, model_root: Path, device: str, compute_type: str, intra_threads: int):
        self.model_root = model_root
        self.device = device
        self.compute_type = compute_type
        self.intra_threads = intra_threads
        self._models = {}

    def list_supported_pairs(self):
        pairs = []
        if self.model_root.exists():
            for entry in sorted(self.model_root.iterdir()):
                if entry.is_dir() and (entry / "model.bin").exists():
                    pairs.append(entry.name.replace("_", "-"))
        return pairs

    def translate(self, pair: str, text: str) -> str:
        runtime = self._load_pair(pair)
        tokenizer = runtime["tokenizer"]
        translator = runtime["translator"]

        tokens = tokenizer.encode(pair, text)

        results = translator.translate_batch([tokens], max_batch_size=1)
        hypothesis = results[0].hypotheses[0]
        return tokenizer.decode(hypothesis)

    def _load_pair(self, pair: str):
        runtime = self._models.get(pair)
        if runtime is not None:
            return runtime

        model_dir = self.model_root / pair
        if not model_dir.exists():
            raise FileNotFoundError(f"Model directory not found: {model_dir}")

        translator = ctranslate2.Translator(
            str(model_dir),
            device=self.device,
            compute_type=self.compute_type,
            inter_threads=1,
            intra_threads=self.intra_threads,
        )
        tokenizer = PairTokenizer(model_dir)
        runtime = {"translator": translator, "tokenizer": tokenizer}
        self._models[pair] = runtime
        return runtime


def respond(payload):
    sys.stdout.write(json.dumps(payload, ensure_ascii=False) + "\n")
    sys.stdout.flush()


def main():
    configure_stdio()
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-root", required=True)
    parser.add_argument("--device", default="cpu")
    parser.add_argument("--compute-type", default="int8")
    parser.add_argument("--intra-threads", type=int, default=4)
    args = parser.parse_args()

    worker = Worker(Path(args.model_root), args.device, args.compute_type, args.intra_threads)

    for raw_line in sys.stdin:
        line = raw_line.strip()
        if not line:
            continue
        try:
            request = json.loads(line)
            command = request.get("cmd")
            if command == "list_supported_pairs":
                respond({"ok": True, "pairs": worker.list_supported_pairs()})
            elif command == "translate":
                pair = request["pair"]
                text = request["text"]
                respond({"ok": True, "text": worker.translate(pair, text)})
            elif command == "shutdown":
                respond({"ok": True})
                return 0
            else:
                respond({"ok": False, "error": f"Unknown command: {command}"})
        except Exception as exc:
            respond({"ok": False, "error": str(exc)})
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
