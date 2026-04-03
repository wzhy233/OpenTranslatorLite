package io.github.wzhy233.open_translator.setup;

public final class SetupWizardLauncher {
    private SetupWizardLauncher() {
    }

    public static void main(String[] args) {
        SetupWizard wizard = new SetupWizard(RuntimeSetupManager.inspect());
        wizard.showDialog();
    }
}
