package io.github.wzhy233.open_translator.setup;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import io.github.wzhy233.open_translator.config.ConfigManager;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SetupWizard {
    private static final String[] STEP_IDS = {"overview", "guide", "license", "setup", "finish"};
    private static final String[] STEP_TITLES = {"Overview", "Guide", "License", "Runtime", "Finish"};

    private final JDialog dialog;
    private final JLabel pythonStatus = new JLabel();
    private final JLabel dependencyStatus = new JLabel();
    private final JLabel modelStatus = new JLabel();
    private final JLabel licenseStatus = new JLabel();
    private final JLabel deviceStatus = new JLabel();
    private final JLabel finishSummary = new JLabel();
    private final JTextField pythonField = new JTextField();
    private final JTextField modelRootField = new JTextField();
    private final JTextField signerField = new JTextField();
    private final JTextArea logArea = new JTextArea();
    private final JProgressBar progressBar = new JProgressBar();
    private final JPanel progressPanel = new JPanel(new BorderLayout());
    private final JButton nextButton = new JButton("Next");
    private final JButton backButton = new JButton("Back");
    private final JButton closeButton = new JButton("Close");
    private final JButton themeButton = new JButton();
    private final JLabel headerTitle = new JLabel("OpenTranslatorLite Setup");
    private final JLabel headerSubtitle = new JLabel("Every device must complete setup and sign the license before runtime use.");
    private final JLabel railCaption = new JLabel("Setup Flow");
    private final JLabel[] stepLabels = new JLabel[STEP_IDS.length];
    private final AnimatedCards cards = new AnimatedCards();

    private int currentStep;
    private SetupStatus currentStatus;

    public SetupWizard(SetupStatus initialStatus) {
        installLookAndFeel();
        dialog = new JDialog((JFrame) null, "OpenTranslatorLite Setup", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setMinimumSize(new Dimension(1100, 760));
        dialog.setSize(new Dimension(1100, 760));
        dialog.setLocationRelativeTo(null);
        dialog.setLayout(new BorderLayout());

        dialog.add(buildHeader(), BorderLayout.NORTH);
        dialog.add(buildBody(), BorderLayout.CENTER);
        dialog.add(buildFooter(), BorderLayout.SOUTH);

        cards.addCard("overview", buildOverviewCard());
        cards.addCard("guide", buildGuideCard());
        cards.addCard("license", buildLicenseCard());
        cards.addCard("setup", buildSetupCard());
        cards.addCard("finish", buildFinishCard());

        currentStatus = initialStatus;
        applyStatus(initialStatus);
        goToStep(0, false);
        applyTheme();
    }

    public void showDialog() {
        dialog.setVisible(true);
    }

    private JPanel buildHeader() {
        JPanel header = new GradientPanel();
        header.setLayout(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        headerTitle.setFont(headerTitle.getFont().deriveFont(Font.BOLD, 28f));
        headerSubtitle.setFont(headerSubtitle.getFont().deriveFont(Font.PLAIN, 14f));

        text.add(headerTitle);
        text.add(Box.createVerticalStrut(8));
        text.add(headerSubtitle);

        themeButton.addActionListener(this::toggleTheme);
        updateThemeButton();

        header.add(text, BorderLayout.WEST);
        header.add(themeButton, BorderLayout.EAST);
        return header;
    }

    private JPanel buildBody() {
        JPanel body = new JPanel(new BorderLayout());
        body.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        body.add(buildStepRail(), BorderLayout.WEST);
        body.add(cards, BorderLayout.CENTER);
        return body;
    }

    private JPanel buildStepRail() {
        JPanel rail = new JPanel();
        rail.setLayout(new BoxLayout(rail, BoxLayout.Y_AXIS));
        rail.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")),
                BorderFactory.createEmptyBorder(18, 18, 18, 18)
        ));
        rail.setPreferredSize(new Dimension(210, 10));

        railCaption.setFont(railCaption.getFont().deriveFont(Font.BOLD, 16f));
        rail.add(railCaption);
        rail.add(Box.createVerticalStrut(18));

        for (int i = 0; i < STEP_TITLES.length; i++) {
            JLabel label = new JLabel((i + 1) + ". " + STEP_TITLES[i]);
            label.setOpaque(true);
            label.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            stepLabels[i] = label;
            rail.add(label);
            rail.add(Box.createVerticalStrut(10));
        }

        rail.add(Box.createVerticalGlue());
        JButton openProject = new JButton("Project Link");
        openProject.setAlignmentX(Component.LEFT_ALIGNMENT);
        openProject.addActionListener(e -> openLink("https://github.com/wzhy233/OpenTranslatorLite"));
        rail.add(openProject);
        return rail;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(BorderFactory.createEmptyBorder(0, 18, 18, 18));

        progressBar.setIndeterminate(false);
        progressPanel.add(progressBar, BorderLayout.CENTER);
        progressPanel.setVisible(false);
        footer.add(progressPanel, BorderLayout.NORTH);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        backButton.addActionListener(e -> goToStep(currentStep - 1, true));
        nextButton.addActionListener(this::handleNext);
        closeButton.addActionListener(e -> dialog.dispose());
        buttons.add(backButton);
        buttons.add(nextButton);
        buttons.add(closeButton);
        footer.add(buttons, BorderLayout.SOUTH);
        return footer;
    }

    private JPanel buildOverviewCard() {
        JPanel panel = createCardPanel();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 14, 0);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.weightx = 1;

        panel.add(sectionTitle("Readiness"), gbc);
        gbc.gridy++;
        panel.add(statusCard("Device", deviceStatus), gbc);
        gbc.gridy++;
        panel.add(statusCard("Python", pythonStatus), gbc);
        gbc.gridy++;
        panel.add(statusCard("Dependencies", dependencyStatus), gbc);
        gbc.gridy++;
        panel.add(statusCard("Models", modelStatus), gbc);
        gbc.gridy++;
        panel.add(statusCard("License", licenseStatus), gbc);
        gbc.gridy++;

        JLabel note = new JLabel("Finish remains locked until this device is fully configured and licensed.");
        note.setForeground(UIManager.getColor("Component.error.focusedBorderColor"));
        panel.add(note, gbc);
        gbc.gridy++;
        gbc.weighty = 1;
        panel.add(Box.createVerticalGlue(), gbc);
        return panel;
    }

    private JScrollPane buildGuideCard() {
        String markdown = readTextResource("/docs/USER_GUIDE.md");
        JEditorPane pane = createHtmlPane(renderMarkdown(markdown), true);
        JScrollPane scrollPane = new JScrollPane(pane);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        return scrollPane;
    }

    private JPanel buildLicenseCard() {
        JPanel panel = createCardPanel();
        panel.setLayout(new BorderLayout(16, 16));

        JEditorPane licenseView = createHtmlPane(readTextResource("/LICENSE.txt"), false);
        panel.add(new JScrollPane(licenseView), BorderLayout.CENTER);

        JPanel controls = new JPanel(new BorderLayout(12, 12));
        controls.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        signerField.setText(ConfigManager.getLicenseSigner());
        controls.add(new JLabel("Signer name"), BorderLayout.WEST);
        controls.add(signerField, BorderLayout.CENTER);

        JButton signButton = new JButton("Sign");
        signButton.addActionListener(e -> {
            String signer = signerField.getText().trim();
            if (signer.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Signer name is required.");
                return;
            }
            RuntimeSetupManager.acceptLicense(signer);
            appendLog("License saved for this device.");
            refreshStatus();
            if (currentStatus.isReady()) {
                goToStep(currentStep + 1, true);
            }
        });
        controls.add(signButton, BorderLayout.EAST);
        panel.add(controls, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildSetupCard() {
        JPanel panel = createCardPanel();
        panel.setLayout(new BorderLayout(18, 18));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 12, 12);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;

        pythonField.setText(currentStatus == null ? "" : currentStatus.pythonPath);
        modelRootField.setText(ConfigManager.getModelRoot());

        form.add(new JLabel("Python executable"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        form.add(pythonField, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0;
        JButton browsePython = new JButton("Browse");
        browsePython.addActionListener(e -> choosePath(pythonField, false));
        form.add(browsePython, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        form.add(new JLabel("Model root"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        form.add(modelRootField, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0;
        JButton browseRoot = new JButton("Browse");
        browseRoot.addActionListener(e -> choosePath(modelRootField, true));
        form.add(browseRoot, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 3;
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        JButton saveButton = new JButton("Save Paths");
        saveButton.addActionListener(e -> {
            savePaths();
            appendLog("Saved runtime paths.");
        });
        JButton installDeps = new JButton("Install Dependencies");
        installDeps.addActionListener(e -> runAsyncSetup("Installing Python dependencies",
                () -> RuntimeSetupManager.installDependencies(pythonField.getText().trim(), this::appendLog)));
        JButton downloadModels = new JButton("Download Models");
        downloadModels.addActionListener(e -> runAsyncSetup("Downloading models",
                () -> RuntimeSetupManager.downloadModels(
                        pythonField.getText().trim(),
                        Path.of(modelRootField.getText().trim()),
                        this::appendLog)));
        actions.add(saveButton);
        actions.add(installDeps);
        actions.add(downloadModels);
        form.add(actions, gbc);

        panel.add(form, BorderLayout.NORTH);

        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildFinishCard() {
        JPanel panel = createCardPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel title = sectionTitle("Setup Complete");
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        finishSummary.setAlignmentX(Component.LEFT_ALIGNMENT);
        finishSummary.setBorder(BorderFactory.createEmptyBorder(18, 0, 0, 0));

        panel.add(title);
        panel.add(Box.createVerticalStrut(12));
        panel.add(finishSummary);
        panel.add(Box.createVerticalStrut(20));

        JButton openConfig = new JButton("Open Config Folder");
        openConfig.setAlignmentX(Component.LEFT_ALIGNMENT);
        openConfig.addActionListener(e -> openLink(Path.of(ConfigManager.getConfigFilePath()).getParent().toUri().toString()));
        panel.add(openConfig);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private JPanel createCardPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")),
                BorderFactory.createEmptyBorder(20, 20, 20, 20)
        ));
        return panel;
    }

    private JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 22f));
        return label;
    }

    private JPanel statusCard(String title, JLabel valueLabel) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)
        ));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        valueLabel.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        card.add(titleLabel);
        card.add(valueLabel);
        return card;
    }

    private void handleNext(ActionEvent event) {
        if (currentStep == 2 && !currentStatus.licenseAccepted) {
            JOptionPane.showMessageDialog(dialog, "Sign the license for this device before continuing.");
            return;
        }
        if (currentStep == 4) {
            if (currentStatus.isReady()) {
                dialog.dispose();
            } else {
                JOptionPane.showMessageDialog(dialog, currentStatus.describeProblems());
            }
            return;
        }
        goToStep(currentStep + 1, true);
    }

    private void goToStep(int stepIndex, boolean animate) {
        int bounded = Math.max(0, Math.min(STEP_IDS.length - 1, stepIndex));
        currentStep = bounded;
        cards.showCard(STEP_IDS[bounded], animate);
        updateStepRail();
        backButton.setEnabled(bounded > 0);
        nextButton.setText(bounded == STEP_IDS.length - 1 ? "Finish" : "Next");
        nextButton.setEnabled(bounded != 2 || currentStatus.licenseAccepted);
        closeButton.setVisible(bounded != STEP_IDS.length - 1);
    }

    private void updateStepRail() {
        Color activeBackground = UIManager.getColor("Button.default.background");
        Color activeForeground = UIManager.getColor("Button.default.foreground");
        Color normalBackground = UIManager.getColor("Panel.background");
        Color normalForeground = UIManager.getColor("Label.foreground");
        for (int i = 0; i < stepLabels.length; i++) {
            JLabel label = stepLabels[i];
            boolean active = i == currentStep;
            label.setBackground(active ? activeBackground : normalBackground);
            label.setForeground(active ? activeForeground : normalForeground);
        }
    }

    private void refreshStatus() {
        applyStatus(RuntimeSetupManager.inspect());
    }

    private void applyStatus(SetupStatus status) {
        currentStatus = status;
        pythonField.setText(status.pythonPath == null ? "" : status.pythonPath);
        modelRootField.setText(status.modelRoot == null ? "" : status.modelRoot.toString());

        String signedDevice = ConfigManager.getSignedDeviceFingerprint();
        String currentDevice = ConfigManager.currentDeviceFingerprint();
        String shortCurrent = currentDevice.substring(0, Math.min(12, currentDevice.length()));
        deviceStatus.setText(signedDevice.equals(currentDevice)
                ? "This device is matched: " + shortCurrent
                : "Current device requires setup: " + shortCurrent);
        pythonStatus.setText(status.pythonAvailable ? "Python is available" : "Python executable is missing or invalid");
        dependencyStatus.setText(status.dependenciesInstalled ? "Required packages are installed" : "Python packages still need installation");
        modelStatus.setText(status.hasModels() ? "All language pairs are present"
                : "Missing model pairs: " + String.join(", ", status.getMissingPairs()));
        licenseStatus.setText(status.licenseAccepted
                ? "Signed by " + status.licenseSigner + " at " + formatSignedAt(ConfigManager.getLicenseSignedAt())
                : "License is not signed for this device");

        finishSummary.setText("<html><body style='width:600px'>"
                + (status.isReady()
                ? "Runtime is ready. Models, Python environment, license are all valid."
                : "Setup is still incomplete. Finish stays locked until runtime checks pass for this device.")
                + "</body></html>");

        nextButton.setEnabled(currentStep != 2 || status.licenseAccepted);
        closeButton.setVisible(currentStep != STEP_IDS.length - 1);
        if (status.isReady() && currentStep == STEP_IDS.length - 2) {
            goToStep(STEP_IDS.length - 1, true);
        }
    }

    private void savePaths() {
        RuntimeSetupManager.savePaths(pythonField.getText().trim(), Path.of(modelRootField.getText().trim()));
        refreshStatus();
    }

    private void runAsyncSetup(String title, Runnable action) {
        savePaths();
        progressPanel.setVisible(true);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        appendLog("== " + title + " ==");
        backButton.setEnabled(false);
        nextButton.setEnabled(false);
        dialog.revalidate();
        dialog.repaint();

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                action.run();
                return null;
            }

            @Override
            protected void done() {
                progressBar.setIndeterminate(false);
                progressBar.setVisible(false);
                progressPanel.setVisible(false);
                backButton.setEnabled(currentStep > 0);
                try {
                    get();
                    appendLog(title + " completed.");
                    refreshStatus();
                } catch (Exception e) {
                    appendLog("ERROR: " + e.getMessage());
                    JOptionPane.showMessageDialog(dialog, e.getMessage(), "Setup failed", JOptionPane.ERROR_MESSAGE);
                    refreshStatus();
                }
                dialog.revalidate();
                dialog.repaint();
            }
        }.execute();
    }

    private void appendLog(String line) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(line + System.lineSeparator());
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void choosePath(JTextField target, boolean directoryOnly) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(directoryOnly ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
        int result = chooser.showOpenDialog(dialog);
        if (result == JFileChooser.APPROVE_OPTION) {
            target.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void toggleTheme(ActionEvent event) {
        String next = "dark".equalsIgnoreCase(ConfigManager.getUiTheme()) ? "light" : "dark";
        ConfigManager.setUiTheme(next);
        applyTheme();
    }

    private void applyTheme() {
        try {
            if ("light".equalsIgnoreCase(ConfigManager.getUiTheme())) {
                FlatLightLaf.setup();
            } else {
                FlatDarculaLaf.setup();
            }
            SwingUtilities.updateComponentTreeUI(dialog);
            updateThemeButton();
            updateThemeState();
        } catch (Exception ignored) {
        }
    }

    private void updateThemeButton() {
        boolean dark = !"light".equalsIgnoreCase(ConfigManager.getUiTheme());
        themeButton.setText(dark ? "Light Mode" : "Dark Mode");
    }

    private void updateThemeState() {
        boolean dark = !"light".equalsIgnoreCase(ConfigManager.getUiTheme());
        Color labelForeground = UIManager.getColor("Label.foreground");
        Color headerForeground = dark ? new Color(246, 248, 252) : new Color(22, 28, 36);
        Color subtitleForeground = dark ? new Color(214, 224, 238) : new Color(56, 68, 84);
        Color stepBackground = UIManager.getColor("Panel.background");

        headerTitle.setForeground(headerForeground);
        headerSubtitle.setForeground(subtitleForeground);
        railCaption.setForeground(labelForeground);
        logArea.setBackground(UIManager.getColor("TextArea.background"));
        logArea.setForeground(labelForeground);
        logArea.setCaretColor(labelForeground);
        progressPanel.setBackground(UIManager.getColor("Panel.background"));
        progressBar.setVisible(progressPanel.isVisible());

        for (JLabel stepLabel : stepLabels) {
            if (stepLabel != null) {
                stepLabel.setBackground(stepBackground);
                stepLabel.setForeground(labelForeground);
            }
        }

        updateStepRail();
        dialog.revalidate();
        dialog.repaint();
    }

    private JEditorPane createHtmlPane(String content, boolean html) {
        JEditorPane pane = new JEditorPane();
        pane.setEditable(false);
        pane.setContentType(html ? "text/html" : "text/plain");
        pane.setText(content);
        pane.setCaretPosition(0);
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.addHyperlinkListener(event -> {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                openLink(event.getURL().toString());
            }
        });
        return pane;
    }

    private static void installLookAndFeel() {
        try {
            if ("light".equalsIgnoreCase(ConfigManager.getUiTheme())) {
                FlatLightLaf.setup();
            } else {
                FlatDarculaLaf.setup();
            }
        } catch (Exception ignored) {
        }
    }

    private static String renderMarkdown(String markdown) {
        Parser parser = Parser.builder().build();
        HtmlRenderer renderer = HtmlRenderer.builder().build();
        return renderer.render(parser.parse(markdown));
    }

    private static String readTextResource(String resourcePath) {
        try (InputStream input = SetupWizard.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                return "Missing resource: " + resourcePath;
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Failed to load resource: " + resourcePath;
        }
    }

    private static void openLink(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception ignored) {
        }
    }

    private static String formatSignedAt(long value) {
        if (value <= 0L) {
            return "unknown time";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date(value));
    }

    private static final class GradientPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            Color c1 = UIManager.getColor("Button.default.background");
            Color c2 = UIManager.getColor("Component.focusColor");
            if (c1 == null) {
                c1 = new Color(34, 82, 121);
            }
            if (c2 == null) {
                c2 = new Color(21, 41, 65);
            }
            g2.setPaint(new GradientPaint(0, 0, c1, getWidth(), getHeight(), c2));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
            g2.dispose();
        }
    }

    private static final class AnimatedCards extends JPanel {
        private final CardLayout layout = new CardLayout();
        private BufferedImage overlay;
        private float alpha;
        private Timer timer;

        private AnimatedCards() {
            setLayout(layout);
        }

        private void addCard(String name, Component component) {
            super.add(component, name);
        }

        private void showCard(String name, boolean animate) {
            if (!animate || getWidth() <= 0 || getHeight() <= 0) {
                layout.show(this, name);
                repaint();
                return;
            }

            overlay = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = overlay.createGraphics();
            super.paint(g2);
            g2.dispose();

            layout.show(this, name);
            alpha = 1f;
            if (timer != null && timer.isRunning()) {
                timer.stop();
            }
            timer = new Timer(16, event -> {
                alpha -= 0.08f;
                if (alpha <= 0f) {
                    alpha = 0f;
                    overlay = null;
                    ((Timer) event.getSource()).stop();
                }
                repaint();
            });
            timer.start();
        }

        @Override
        public void paint(Graphics g) {
            super.paint(g);
            if (overlay != null && alpha > 0f) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setComposite(java.awt.AlphaComposite.SrcOver.derive(alpha));
                g2.drawImage(overlay, 0, 0, null);
                g2.dispose();
            }
        }
    }
}
