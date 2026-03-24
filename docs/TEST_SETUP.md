# Test Setup

## Goal

Tests in this project use the real CTranslate2 runtime and real translation models.

They do not use a fake worker anymore.

## Test Setup Flow

The shared test bootstrap is implemented in:

- `src/test/java/io/github/wzhy233/open_translator/TestSetup.java`

The bootstrap does these steps before tests start:

1. Choose UI setup on desktop or automatic setup in headless mode
2. Detect a Python executable
3. Install Python dependencies from `scripts/requirements.txt`
4. Download real CTranslate2 models if they are missing
5. Write runtime config values
6. Accept the license for the current device under the test signer name

## UI Behavior

Test setup now supports both interactive and non-interactive modes.

- Default on desktop: use setup UI
- Headless environment: fall back to automated setup
- Force non-interactive mode:

```bash
mvn test -Dopen_translator.test_setup_ui=false
```

## What `mvn test` Requires

- Internet access for the first run
- A working Python installation
- Enough disk space for the two model directories

## Model Location Used By Tests

```text
~/.open_translator/OpenTranslatorLite/models/ctranslate2
```

## Notes

- The first test run is slower because dependencies and models may need to be downloaded.
- Later test runs are much faster because models and Python packages are reused.
- Tests still validate real UTF-8 output, including asynchronous translation.
