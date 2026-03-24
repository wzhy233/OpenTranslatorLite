import argparse
import shutil
from pathlib import Path

from huggingface_hub import snapshot_download


MODELS = {
    "en_zh": "gaudi/opus-mt-en-zh-ctranslate2",
    "zh_en": "gaudi/opus-mt-zh-en-ctranslate2",
}


def parse_args():
    parser = argparse.ArgumentParser(description="Download CTranslate2 models for OpenTranslatorLite")
    parser.add_argument("--model-root", required=True)
    parser.add_argument("--pair", choices=("en_zh", "zh_en", "all"), default="all")
    parser.add_argument("--force", action="store_true")
    return parser.parse_args()


def download_pair(pair, repo_id, model_root, force):
    local_dir = model_root / pair
    if force and local_dir.exists():
        shutil.rmtree(local_dir)
    local_dir.mkdir(parents=True, exist_ok=True)
    print(f"Downloading {pair} from {repo_id} -> {local_dir}")
    snapshot_download(
        repo_id=repo_id,
        local_dir=str(local_dir),
        allow_patterns=[
            "model.bin",
            "config.json",
            "generation_config.json",
            "shared_vocabulary.json",
            "source.spm",
            "target.spm",
            "tokenizer_config.json",
            "vocab.json",
            "README.md",
        ],
    )


def main():
    args = parse_args()
    model_root = Path(args.model_root).expanduser().resolve()
    model_root.mkdir(parents=True, exist_ok=True)

    pairs = MODELS.items() if args.pair == "all" else [(args.pair, MODELS[args.pair])]
    for pair, repo_id in pairs:
        download_pair(pair, repo_id, model_root, args.force)

    print(f"Models ready under: {model_root}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
