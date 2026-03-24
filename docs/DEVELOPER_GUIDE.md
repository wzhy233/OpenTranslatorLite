# Developer Guide

## Architecture

OpenTranslatorLite now uses this runtime structure:

1. Java public API in `Translator`
2. Runtime readiness checks in `setup/RuntimeSetupManager`
3. Setup UI in `setup/SetupWizard`
4. Python worker process in `src/main/resources/python/ctranslate2_worker.py`
5. External CTranslate2 model directories on disk

## Runtime Rules

- The library must not be used until setup is complete
- License acceptance is enforced per device
- Missing Python, dependencies, models, or license state blocks startup
- Desktop environments can open the setup UI automatically

## Important Config Keys

- `open_translator.python`
- `open_translator.model_root`
- `open_translator.ui.enabled`
- `open_translator.compute_type`
- `open_translator.device`

## Setup UI

The setup UI is intentionally part of the runtime path, not just a helper tool.

It is responsible for:

- Environment inspection
- Dependency installation
- Model download
- Markdown document rendering
- License signing
- Device-bound acceptance gating
- Light/dark theme switching and animated wizard transitions

## Packaging Model

- The jar is intentionally small
- Translation models are external
- Python dependencies are external
- Project docs and license files are included in the jar resources

## Recommended Distribution Model

If another application embeds this library, that application should:

1. Allow the setup UI to appear on first use
2. Or provide an explicit setup action before creating `Translator`
3. Preserve the runtime config directory for the target user

## Related Docs

- `docs/USER_GUIDE.md`
- `docs/TEST_SETUP.md`
- `docs/API.md`
