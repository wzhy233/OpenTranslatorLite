# OpenTranslatorLite User Guide

## First Launch

OpenTranslatorLite now checks four things before translation starts:

- Python executable
- Required Python packages
- External CTranslate2 models
- License acceptance

If any item is missing and the environment has a desktop session, the setup wizard opens automatically.

License acceptance is stored and validated per device. On a new device or a new user profile, the first use must go through setup and sign the license again.

This rule applies to every startup path. If setup is incomplete, runtime use is blocked.

## What The Wizard Can Do

- Render this Markdown guide inside the app
- Show project and dependency links
- Let the user read and accept the license with a signer name
- Save the signed license
- Configure Python path and model directory
- Install Python dependencies
- Download translation models
- Block final completion until the current device is licensed and ready
- Support light and dark modes
- Use animated card transitions between setup steps

## Default Paths

Cache directory:

```text
~/.open_translator/OpenTranslatorLite/cache
```

Model directory:

```text
~/.open_translator/OpenTranslatorLite/models/ctranslate2
```

## Manual Setup

If you want to set up the runtime without opening the wizard:

```bash
pip install -r scripts/requirements.txt
python scripts/init_models.py
```

## Embedded Use

If another developer distributes an application that uses this library, the end user still needs:

- Python available on the machine
- The required Python packages
- Downloaded model files
- Accepted license state

The setup wizard is intended to guide that first-run experience automatically.

## Links

- Project: https://github.com/wzhy233/OpenTranslatorLite
- CTranslate2: https://github.com/OpenNMT/CTranslate2
- Models: https://huggingface.co/gaudi/opus-mt-en-zh-ctranslate2
