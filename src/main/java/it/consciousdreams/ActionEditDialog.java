package it.consciousdreams;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;

import static java.awt.event.KeyEvent.*;
import java.util.List;

public class ActionEditDialog extends DialogWrapper {

    static final String         DEFAULT_ICON_PATH  = "/icons/maven_install.svg";
    static final String         TESTS_ICON_PATH    = "/icons/maven_install_with_tests.svg";
    static final String         GRADLE_ICON_PATH   = "/icons/gradle.svg";
    static final String         NPM_ICON_PATH      = "/icons/npm.svg";
    static final String         YARN_ICON_PATH     = "/icons/yarn.svg";
    static final String         MAKE_ICON_PATH     = "/icons/make.svg";
    static final String         SHELL_ICON_PATH    = "/icons/shell.svg";
    static final String         DOCKER_ICON_PATH   = "/icons/docker.svg";

    static final List<String[]> AVAILABLE_ICONS    = List.of(
            new String[]{DEFAULT_ICON_PATH, "Maven (skip tests)"},
            new String[]{TESTS_ICON_PATH,   "Maven (with tests)"},
            new String[]{GRADLE_ICON_PATH,  "Gradle"},
            new String[]{NPM_ICON_PATH,     "npm"},
            new String[]{YARN_ICON_PATH,    "Yarn"},
            new String[]{MAKE_ICON_PATH,    "Make"},
            new String[]{SHELL_ICON_PATH,   "Shell"},
            new String[]{DOCKER_ICON_PATH,  "Docker"}
    );

    private final ComboBox<ToolType>         typeCombo;
    private final JLabel                     commandLabel;
    private final JTextField                 labelField;
    private final JTextField                 goalsField;
    private final ComboBox<String[]>         iconCombo;
    private final TextFieldWithBrowseButton  customIconField;
    private final JTextField                 shortcutField;
    private KeyStroke                        capturedKeystroke;

    public ActionEditDialog(@Nullable String label, @Nullable String goals,
                            @Nullable String iconPath, @Nullable String shortcut,
                            @Nullable String commandType) {
        super(true);
        typeCombo       = new ComboBox<>(ToolType.values());
        commandLabel    = new JLabel("Maven Goals:");
        labelField      = new JTextField(label != null ? label : "", 30);
        goalsField      = new JTextField(goals != null ? goals : "", 30);
        iconCombo       = buildIconCombo(iconPath);
        customIconField = buildCustomIconField(iconPath);
        shortcutField   = buildShortcutField(shortcut);

        // Pre-select type
        ToolType selected = ToolType.fromId(commandType);
        typeCombo.setSelectedItem(selected);
        updateCommandLabel(selected);
        if (iconPath == null) autoSelectIcon(selected);

        typeCombo.addActionListener(e -> {
            ToolType t = (ToolType) typeCombo.getSelectedItem();
            if (t == null) return;
            updateCommandLabel(t);
            goalsField.setText(t.template);
            autoSelectIcon(t);
        });

        setTitle(label == null ? "Add Action" : "Edit Action");
        init();
    }

    private void updateCommandLabel(ToolType type) {
        commandLabel.setText(type.isMaven() ? "Maven Goals:" : "Command:");
    }

    // ── Icon auto-suggest ─────────────────────────────────────────────────────

    private void autoSelectIcon(ToolType type) {
        autoSelectIcon(iconCombo, type.iconPath);
    }

    private static void autoSelectIcon(ComboBox<String[]> combo, String path) {
        for (int i = 0; i < AVAILABLE_ICONS.size(); i++) {
            if (AVAILABLE_ICONS.get(i)[0].equals(path)) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    // ── Icon combo ────────────────────────────────────────────────────────────

    private ComboBox<String[]> buildIconCombo(@Nullable String selectedPath) {
        ComboBox<String[]> combo = new ComboBox<>(AVAILABLE_ICONS.toArray(new String[0][]));
        combo.setRenderer(new BasicComboBoxRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof String[] entry && entry[0] != null) {
                    setIcon(IconLoader.getIcon(entry[0], ActionEditDialog.class));
                    setText(entry[1]);
                }
                return this;
            }
        });
        if (selectedPath != null) autoSelectIcon(combo, selectedPath);
        return combo;
    }

    private TextFieldWithBrowseButton buildCustomIconField(@Nullable String iconPath) {
        TextFieldWithBrowseButton field = new TextFieldWithBrowseButton();
        field.addBrowseFolderListener(
                new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFileDescriptor("svg"))
        );
        if (iconPath != null && !iconPath.startsWith("/icons/")) {
            field.setText(iconPath);
        }
        return field;
    }

    // ── Shortcut field ────────────────────────────────────────────────────────

    private JTextField buildShortcutField(@Nullable String shortcut) {
        JTextField field = new JTextField(22);
        field.setEditable(false);
        field.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));

        if (shortcut != null && !shortcut.isEmpty()) {
            KeyStroke ks = KeyStroke.getKeyStroke(shortcut);
            if (ks != null) {
                capturedKeystroke = ks;
                field.setText(KeymapUtil.getKeystrokeText(ks));
            }
        }

        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int code = e.getKeyCode();
                if (code == VK_SHIFT || code == VK_CONTROL || code == VK_ALT
                        || code == VK_META || code == VK_UNDEFINED) return;
                if (code == VK_ESCAPE && e.getModifiersEx() == 0) {
                    capturedKeystroke = null;
                    field.setText("");
                    e.consume();
                    return;
                }
                capturedKeystroke = KeyStroke.getKeyStrokeForEvent(e);
                field.setText(KeymapUtil.getKeystrokeText(capturedKeystroke));
                e.consume();
            }
        });
        return field;
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(4);
        gbc.anchor = GridBagConstraints.WEST;

        addRow(panel, gbc, 0, "Type:",          typeCombo,        false);
        addRow(panel, gbc, 1, "Label:",          labelField,       true);

        // Command row with dynamic label
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE;
        panel.add(commandLabel, gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(goalsField, gbc);
        gbc.fill = GridBagConstraints.NONE;

        addRow(panel, gbc, 3, "Built-in icon:", iconCombo,        false);
        addRow(panel, gbc, 4, "Custom SVG:",    customIconField,  true);

        // Shortcut row
        gbc.gridx = 0; gbc.gridy = 5; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Shortcut:"), gbc);
        JPanel shortcutRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        shortcutRow.add(shortcutField);
        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(ev -> {
            capturedKeystroke = null;
            shortcutField.setText("");
            shortcutField.requestFocusInWindow();
        });
        shortcutRow.add(clearBtn);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(shortcutRow, gbc);

        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("<html><small>" +
                "Shell command runs via your login shell in the project root.<br>" +
                "Click the Shortcut field and press a key combination. Esc = clear.<br>" +
                "Custom SVG overrides built-in icon when set." +
                "</small></html>"), gbc);

        return panel;
    }

    private void addRow(JPanel panel, GridBagConstraints gbc, int row,
                        String labelText, JComponent field, boolean fillHorizontal) {
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(labelText), gbc);
        gbc.gridx = 1;
        if (fillHorizontal) gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, gbc);
        gbc.fill = GridBagConstraints.NONE;
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (labelField.getText().trim().isEmpty())
            return new ValidationInfo("Label is required", labelField);
        if (goalsField.getText().trim().isEmpty())
            return new ValidationInfo("Command is required", goalsField);
        String custom = customIconField.getText().trim();
        if (!custom.isEmpty()) {
            if (!custom.toLowerCase().endsWith(".svg"))
                return new ValidationInfo("Custom icon must be an SVG file", customIconField.getTextField());
            File file = new File(custom);
            if (!file.exists() || !file.isFile())
                return new ValidationInfo("File not found: " + custom, customIconField.getTextField());
        }
        return null;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String  getLabel()       { return labelField.getText().trim(); }
    public String  getGoals()       { return goalsField.getText().trim(); }
    public String getCommandType() {
        ToolType t = (ToolType) typeCombo.getSelectedItem();
        return t != null ? t.id : ToolType.MAVEN.id;
    }

    public String getIconPath() {
        String custom = customIconField.getText().trim();
        if (!custom.isEmpty()) return custom;
        Object selected = iconCombo.getSelectedItem();
        return selected instanceof String[] s ? s[0] : DEFAULT_ICON_PATH;
    }

    public @Nullable String getShortcut() {
        return capturedKeystroke != null ? capturedKeystroke.toString() : null;
    }
}
