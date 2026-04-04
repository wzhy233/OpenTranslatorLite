package io.github.wzhy233.open_translator.setup;

import io.github.wzhy233.open_translator.runtime.RuntimeManager;


public final class SetupWizardLauncher {
    private SetupWizardLauncher() {
    }

    public static void main(String[] args) {
        SetupWizard wizard = new SetupWizard(RuntimeManager.inspect());
        wizard.showDialog();
    }
}
