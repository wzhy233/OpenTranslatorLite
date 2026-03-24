package io.github.wzhy233.open_translator.setup;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SetupStatus {
    public String pythonPath;
    public Path modelRoot;
    public boolean pythonAvailable;
    public boolean dependenciesInstalled;
    public boolean licenseAccepted;
    public String licenseSigner;
    public final List<String> missingPairs = new ArrayList<>();

    public boolean hasModels() {
        return missingPairs.isEmpty();
    }

    public boolean isReady() {
        return pythonAvailable && dependenciesInstalled && licenseAccepted && hasModels();
    }

    public List<String> getMissingPairs() {
        return Collections.unmodifiableList(missingPairs);
    }

    public String describeProblems() {
        List<String> issues = new ArrayList<>();
        if (!pythonAvailable) {
            issues.add("Python executable not found");
        }
        if (pythonAvailable && !dependenciesInstalled) {
            issues.add("Required Python packages are missing");
        }
        if (!hasModels()) {
            issues.add("Missing models: " + String.join(", ", missingPairs));
        }
        if (!licenseAccepted) {
            issues.add("License has not been accepted");
        }
        return String.join("; ", issues);
    }
}
