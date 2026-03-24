from pathlib import Path
import importlib.util
import shutil
import subprocess
import sys

models = {
    "en_zh": "Helsinki-NLP/Opus-MT-en-zh",
    "zh_en": "Helsinki-NLP/Opus-MT-zh-en"
}

ROOT = Path(__file__).resolve().parents[1]
OUTPUT_DIR = ROOT / "src" / "main" / "resources" / "models" / "pretrained"


def run_cmd(cmd):
    print("Run:", " ".join(cmd))
    subprocess.check_call(cmd)


def run_export(pair: str, model_name: str) -> None:
    tmp_dir = OUTPUT_DIR / f".{pair}_tmp"

    if tmp_dir.exists():
        shutil.rmtree(tmp_dir)
    tmp_dir.mkdir(parents=True, exist_ok=True)

    if importlib.util.find_spec("optimum.exporters.onnx") is not None:
        cmd = [
            sys.executable,
            "-m",
            "optimum.exporters.onnx",
            "-m",
            model_name,
            "--task",
            "text2text-generation-with-past",
            str(tmp_dir),
        ]
    else:
        raise RuntimeError(
            "pip install -r requirements.txt"
        )

    run_cmd(cmd)

    def find_file(names):
        for name in names:
            p = tmp_dir / name
            if p.exists():
                return p
        return None

    encoder = find_file(
        ["encoder_model.onnx", "encoder.onnx", "encoder_model_fp32.onnx"]
    )
    decoder = find_file(
        ["decoder_model.onnx", "decoder.onnx", "decoder_model_fp32.onnx"]
    )
    decoder_with_past = find_file(
        ["decoder_with_past_model.onnx", "decoder_with_past.onnx", "decoder_model.onnx"]
    )

    if encoder is None or decoder is None:
        available = ", ".join(p.name for p in tmp_dir.glob("*"))
        raise RuntimeError(f"{pair} miss: {available}")

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    shutil.move(str(encoder), OUTPUT_DIR / f"{pair}_encoder.onnx")
    shutil.move(str(decoder), OUTPUT_DIR / f"{pair}_decoder.onnx")

    if decoder_with_past is not None:
        shutil.move(
            str(decoder_with_past),
            OUTPUT_DIR / f"{pair}_decoder_with_past.onnx",
            )
    for name in ["source.spm", "target.spm", "vocab.json"]:
        f = tmp_dir / name
        if f.exists():
            shutil.copy(f, OUTPUT_DIR / f"{pair}_{name}")

    shutil.rmtree(tmp_dir, ignore_errors=True)

def main() -> int:
    try:
        import torch  # noqa
        import transformers  # noqa
        import onnx  # noqa
    except Exception:
        print("pip install -r requirements.txt")
        return 1

    print("output dir:", OUTPUT_DIR)

    for pair, model_name in models.items():
        print(f"\n==== {pair} ====")
        run_export(pair, model_name)
        print(f"{pair} complete")

    print("\ndone!")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())