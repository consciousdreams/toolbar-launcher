package it.consciousdreams;

import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.Configurable;
import com.intellij.util.messages.MessageBusConnection;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class ToolbarLauncherConfigurable implements Configurable {

    private JPanel mainPanel;
    private JBTable table;
    private ActionsTableModel tableModel;
    private MessageBusConnection messageBusConnection;

    /**
     * Keymap shortcuts captured at session start (or after Apply).
     * actionId → shortcut string (null = no shortcut).
     * Used to revert keymap changes made via the plugin's own edit dialog on Cancel.
     */
    private Map<String, String> originalKeymapState;

    /**
     * Deep copy of ToolbarLauncherSettings.actions captured at session start (or after Apply).
     * Used to revert settings mutations made by ToolbarLauncherKeymapListener on Cancel,
     * since IntelliJ does not fire shortcutsChanged when it reverts the keymap on Cancel.
     */
    private List<ActionConfig> settingsBackup;

    /**
     * True when updateKeymap() has written to the keymap during this session and the
     * write has not yet been confirmed by Apply or reverted. Guards revertKeymap().
     */
    private boolean keymapDirty = false;

    /**
     * Action IDs registered in ActionManager during this session via addAction(),
     * so the Keymap settings panel shows them immediately. On Apply they are cleared
     * (sync() now owns the registration); on Cancel they are unregistered in
     * disposeUIResources() so the IDE state matches the reverted settings.
     */
    private final Set<String> provisionalActionIds = new HashSet<>();

    /**
     * Pre-session ActionConfig snapshots for actions we mutated in ActionManager (edited
     * or removed) during this session. Keyed by action ID, stored only once per id
     * (on first mutation). On Cancel we re-register each with its snapshot so the IDE
     * state realigns with the restored settings.
     */
    private final Map<String, ActionConfig> originalActionConfigs = new HashMap<>();

    @Override
    public @Nls String getDisplayName() {
        return "Toolbar Launcher";
    }

    @Override
    public @Nullable JComponent createComponent() {
        tableModel = new ActionsTableModel();
        table = createTable();
        configureColumns();

        JPanel decoratedTable = ToolbarDecorator.createDecorator(table)
                .setAddAction(button -> addAction())
                .setRemoveAction(button -> removeAction())
                .setEditAction(button -> editAction())
                .setMoveUpAction(button -> moveRow(-1))
                .setMoveDownAction(button -> moveRow(1))
                .createPanel();

        mainPanel = new JPanel(new BorderLayout(0, 8));
        mainPanel.add(new JLabel("Configure toolbar buttons:"), BorderLayout.NORTH);
        mainPanel.add(decoratedTable, BorderLayout.CENTER);

        captureSessionState();
        keymapDirty = false;
        subscribeToKeymapChanges();
        reset();
        return mainPanel;
    }

    /**
     * Snapshots both the keymap shortcuts and the settings actions.
     * Called on session open and after Apply so that disposeUIResources() can cleanly
     * revert to the last confirmed state on Cancel.
     */
    private void captureSessionState() {
        originalKeymapState = new HashMap<>();
        settingsBackup      = new ArrayList<>();
        for (ActionConfig config : ToolbarLauncherSettings.getInstance().getActions()) {
            String actionId = ActionsRegistrar.PREFIX + config.getId();
            // Use the settings shortcut (last saved value) as the revert target, not the
            // live keymap value. This ensures Cancel always realigns the keymap with the
            // last saved settings, even if previous cancelled sessions left them out of sync.
            originalKeymapState.put(actionId, config.getShortcut());
            settingsBackup.add(config.copy());
        }
    }

    private JBTable createTable() {
        JBTable t = newTableWithTooltip();
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setRowHeight(22);
        t.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                    int row = t.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        t.setRowSelectionInterval(row, row);
                        editAction();
                    }
                }
            }
        });
        return t;
    }

    private JBTable newTableWithTooltip() {
        return new JBTable(tableModel) {
            @Override
            public String getToolTipText(@NotNull MouseEvent e) {
                int col = columnAtPoint(e.getPoint());
                if (col != 0) return super.getToolTipText(e);
                int row = rowAtPoint(e.getPoint());
                if (row < 0) return null;
                return tableModel.getRow(row).isEnabled() ? "Click to disable this button" : "Click to enable this button";
            }
        };
    }

    private void configureColumns() {
        setColumnWidth(0, 28, 28, -1);
        setColumnWidth(1, 28, 28, -1);
        table.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int col) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(t, "", isSelected, hasFocus, row, col);
                if (value instanceof Icon icon) {
                    label.setIcon(icon);
                    label.setHorizontalAlignment(CENTER);
                }
                return label;
            }
        });
        setColumnWidth(2, 55, 55, -1);
        setColumnWidth(3, -1, -1, 160);
        setColumnWidth(4, -1, -1, 230);
        setColumnWidth(5, -1, -1, 100);
    }

    private void setColumnWidth(int col, int min, int max, int preferred) {
        var column = table.getColumnModel().getColumn(col);
        if (min >= 0)       column.setMinWidth(min);
        if (max >= 0)       column.setMaxWidth(max);
        if (preferred >= 0) column.setPreferredWidth(preferred);
    }

    private void subscribeToKeymapChanges() {
        messageBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
        messageBusConnection.subscribe(KeymapManagerListener.TOPIC, new KeymapManagerListener() {
            @Override
            public void shortcutsChanged(@NotNull Keymap keymap, @NotNull Collection<String> actionIds, boolean fromSettings) {
                if (ActionsRegistrar.updatingKeymapFromPlugin) return;

                Keymap active = KeymapManager.getInstance().getActiveKeymap();
                boolean committedToActive = (keymap == active);

                for (String actionId : actionIds) {
                    if (!actionId.startsWith(ActionsRegistrar.PREFIX)) continue;
                    // Read the shortcut from the fired keymap — which may be the IDE's
                    // Keymap settings panel's editing clone. That way draft edits reflect
                    // in our table immediately, without waiting for Apply.
                    String newShortcut = lastKeyboardShortcut(keymap.getShortcuts(actionId));
                    updateTableRowShortcut(actionId, newShortcut);
                    if (committedToActive) {
                        // Real commit on the active keymap → absorb so Cancel does not
                        // revert it in disposeUIResources().
                        absorbExternalKeymapChange(actionId, newShortcut);
                    }
                }
            }

            @Override
            public void activeKeymapChanged(@Nullable Keymap keymap) {
                // New keymap — discard dirty state that belongs to the old keymap.
                keymapDirty = false;
                captureSessionState();
                reset();
            }
        });
    }

    /**
     * Writes the fired keymap's shortcut into the matching tableModel row so the
     * Toolbar Launcher panel reflects Keymap-settings edits (including drafts) immediately.
     */
    private void updateTableRowShortcut(String actionId, @Nullable String newShortcut) {
        if (tableModel == null) return;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            ActionConfig row = tableModel.getRow(i);
            if (!(ActionsRegistrar.PREFIX + row.getId()).equals(actionId)) continue;
            if (!Objects.equals(row.getShortcut(), newShortcut)) {
                row.setShortcut(newShortcut);
                tableModel.fireTableRowsUpdated(i, i);
            }
            return;
        }
    }

    /**
     * Updates {@code originalKeymapState} and {@code settingsBackup} to reflect a
     * shortcut change committed to the ACTIVE keymap outside our own edit dialog.
     * This prevents {@link #disposeUIResources()} from reverting an applied change on
     * Cancel. Not called for draft edits on the Keymap panel's clone, which should be
     * reverted naturally by IntelliJ discarding the clone.
     */
    private void absorbExternalKeymapChange(String actionId, @Nullable String newShortcut) {
        if (originalKeymapState != null) {
            originalKeymapState.put(actionId, newShortcut);
        }
        if (settingsBackup != null) {
            for (ActionConfig c : settingsBackup) {
                if ((ActionsRegistrar.PREFIX + c.getId()).equals(actionId)) {
                    c.setShortcut(newShortcut);
                    break;
                }
            }
        }
    }

    private void addAction() {
        ActionEditDialog dialog = new ActionEditDialog(null, null, null, null, null);
        if (dialog.showAndGet()) {
            ActionConfig config = new ActionConfig(
                    UUID.randomUUID().toString(),
                    dialog.getLabel(),
                    dialog.getGoals(),
                    dialog.getIconPath()
            );
            config.setShortcut(dialog.getShortcut());
            config.setCommandType(dialog.getCommandType());
            tableModel.addRow(config);

            // Register in ActionManager immediately so the new action (and its shortcut)
            // shows up in the Keymap settings panel without waiting for Apply.
            String actionId = ActionsRegistrar.PREFIX + config.getId();
            ActionManager am = ActionManager.getInstance();
            if (am.getAction(actionId) == null) {
                am.registerAction(actionId, new ToolbarAction(config));
                provisionalActionIds.add(actionId);
            }

            updateKeymap(config);
        }
    }

    private void editAction() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        ActionConfig snapshot = tableModel.getRow(row);
        ActionEditDialog dialog = new ActionEditDialog(
                snapshot.getLabel(), snapshot.getGoals(), snapshot.getIconPath(), snapshot.getShortcut(), snapshot.getCommandType());
        if (dialog.showAndGet()) {
            // Re-fetch: reset() may have replaced all rows during showAndGet()'s event loop,
            // making the pre-dialog reference stale.
            ActionConfig config = tableModel.getRow(row);
            String actionId = ActionsRegistrar.PREFIX + config.getId();
            captureOriginalActionConfig(actionId);

            config.setLabel(dialog.getLabel());
            config.setGoals(dialog.getGoals());
            config.setIconPath(dialog.getIconPath());
            config.setShortcut(dialog.getShortcut());
            config.setCommandType(dialog.getCommandType());
            tableModel.fireTableRowsUpdated(row, row);

            // Re-register the ToolbarAction so ActionManager/Keymap panel reflect the new
            // label/icon/goals immediately (the existing instance was constructed with the
            // old values and does not auto-refresh).
            ActionManager am = ActionManager.getInstance();
            if (am.getAction(actionId) != null) am.unregisterAction(actionId);
            am.registerAction(actionId, new ToolbarAction(config));

            updateKeymap(config);
        }
    }

    /**
     * Captures the settings' current ActionConfig for the given id, once per session.
     * Used by editAction/removeAction so Cancel can re-register the action with its
     * pre-session state. No-op for provisionally-added actions (not in settings yet).
     */
    private void captureOriginalActionConfig(String actionId) {
        if (originalActionConfigs.containsKey(actionId)) return;
        for (ActionConfig c : ToolbarLauncherSettings.getInstance().getActions()) {
            if ((ActionsRegistrar.PREFIX + c.getId()).equals(actionId)) {
                originalActionConfigs.put(actionId, c.copy());
                return;
            }
        }
    }

    /**
     * Writes the config's shortcut to the keymap immediately so the Keymap settings panel
     * reflects the change without requiring Apply first. Marks the session dirty so the
     * write is reverted in disposeUIResources() if the user cancels.
     */
    private void updateKeymap(ActionConfig config) {
        ActionsRegistrar.updatingKeymapFromPlugin = true;
        try {
            Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
            String actionId = ActionsRegistrar.PREFIX + config.getId();

            // Capture the original state for this action on its first edit in the session
            // (computeIfAbsent preserves the first captured value across multiple edits).
            if (originalKeymapState != null) {
                originalKeymapState.computeIfAbsent(actionId,
                        id -> lastKeyboardShortcut(keymap.getShortcuts(id)));
            }

            for (Shortcut s : keymap.getShortcuts(actionId)) {
                if (s.isKeyboard()) keymap.removeShortcut(actionId, s);
            }
            KeyStroke ks = KeyStroke.getKeyStroke(config.getShortcut());
            if (ks != null) keymap.addShortcut(actionId, new KeyboardShortcut(ks, null));
        } finally {
            ActionsRegistrar.updatingKeymapFromPlugin = false;
        }
        ActionsRegistrar.keymapBaseline.put(ActionsRegistrar.PREFIX + config.getId(), config.getShortcut());
        keymapDirty = true;
    }

    /**
     * Reverts keymap shortcuts to the state captured at session start.
     * No-op when the session is clean (no updateKeymap() calls since last capture).
     */
    private void revertKeymap() {
        if (!keymapDirty || originalKeymapState == null) return;
        ActionsRegistrar.updatingKeymapFromPlugin = true;
        try {
            Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
            for (Map.Entry<String, String> entry : originalKeymapState.entrySet()) {
                String actionId = entry.getKey();
                String original = entry.getValue();
                for (Shortcut s : keymap.getShortcuts(actionId)) {
                    if (s.isKeyboard()) keymap.removeShortcut(actionId, s);
                }
                if (original != null) {
                    KeyStroke ks = KeyStroke.getKeyStroke(original);
                    if (ks != null) keymap.addShortcut(actionId, new KeyboardShortcut(ks, null));
                }
                ActionsRegistrar.keymapBaseline.put(actionId, original);
            }
        } finally {
            ActionsRegistrar.updatingKeymapFromPlugin = false;
        }
        keymapDirty = false;
    }

    private void removeAction() {
        int row = table.getSelectedRow();
        if (row >= 0) tableModel.removeRow(row);
    }

    private void moveRow(int delta) {
        int row = table.getSelectedRow();
        if (row < 0) return;
        int target = row + delta;
        if (target < 0 || target >= tableModel.getRowCount()) return;
        tableModel.moveRow(row, target);
        table.setRowSelectionInterval(target, target);
    }

    @Override
    public boolean isModified() {
        if (tableModel == null) return false;
        List<ActionConfig> saved  = ToolbarLauncherSettings.getInstance().getActions();
        List<ActionConfig> edited = tableModel.getRows();
        if (saved.size() != edited.size()) return true;
        for (int i = 0; i < saved.size(); i++) {
            if (!saved.get(i).equals(edited.get(i))) return true;
        }
        return false;
    }

    @Override
    public void apply() {
        List<ActionConfig> rows = tableModel.getRows();

        // Full keymap sync: write all current shortcuts and clean up deleted actions.
        // updateKeymap() already wrote individual changes as the user edited, but apply()
        // is authoritative — it also handles enabled/disabled toggles and deletions.
        ActionsRegistrar.updatingKeymapFromPlugin = true;
        try {
            Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
            Set<String> keptIds = new HashSet<>();
            for (ActionConfig config : rows) {
                String actionId = ActionsRegistrar.PREFIX + config.getId();
                keptIds.add(actionId);
                for (Shortcut s : keymap.getShortcuts(actionId)) {
                    if (s.isKeyboard()) keymap.removeShortcut(actionId, s);
                }
                if (config.isEnabled()) {
                    KeyStroke ks = KeyStroke.getKeyStroke(config.getShortcut());
                    if (ks != null) keymap.addShortcut(actionId, new KeyboardShortcut(ks, null));
                }
                ActionsRegistrar.keymapBaseline.put(actionId, config.getShortcut());
            }
            // Remove shortcuts for actions deleted during this session
            for (ActionConfig saved : ToolbarLauncherSettings.getInstance().getActions()) {
                String actionId = ActionsRegistrar.PREFIX + saved.getId();
                if (!keptIds.contains(actionId)) {
                    for (Shortcut s : keymap.getShortcuts(actionId)) {
                        if (s.isKeyboard()) keymap.removeShortcut(actionId, s);
                    }
                }
            }
        } finally {
            ActionsRegistrar.updatingKeymapFromPlugin = false;
        }

        ToolbarLauncherSettings.getInstance().setActions(rows.stream().map(ActionConfig::copy).toList());
        ActionsRegistrar.sync();

        // Re-capture so a subsequent disposeUIResources() (when the dialog closes after Apply)
        // has nothing to revert.
        captureSessionState();
        keymapDirty = false;
        // sync() has now registered these actions permanently — they are no longer provisional.
        provisionalActionIds.clear();
    }

    @Override
    public void reset() {
        if (tableModel == null) return;
        // NOTE: do NOT call revertKeymap() here. IntelliJ calls reset() both for the explicit
        // Reset button AND during panel navigation within the settings dialog. Reverting on
        // navigation would undo good in-progress changes. The Cancel path is handled exclusively
        // in disposeUIResources().
        Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
        List<ActionConfig> copies = new ArrayList<>();
        for (ActionConfig config : ToolbarLauncherSettings.getInstance().getActions()) {
            ActionConfig copy = config.copy();
            if (copy.isEnabled()) {
                // Read from the live keymap so the panel always reflects the current shortcut,
                // even when it was changed via the Keymap settings panel.
                copy.setShortcut(lastKeyboardShortcut(keymap.getShortcuts(ActionsRegistrar.PREFIX + copy.getId())));
            }
            copies.add(copy);
        }
        tableModel.setRows(copies);
    }

    private void reset(Keymap keymap) {
        if (tableModel == null) return;
        List<ActionConfig> copies = new ArrayList<>();
        for (ActionConfig config : ToolbarLauncherSettings.getInstance().getActions()) {
            ActionConfig copy = config.copy();
            if (copy.isEnabled()) {
                copy.setShortcut(lastKeyboardShortcut(keymap.getShortcuts(ActionsRegistrar.PREFIX + copy.getId())));
            }
            copies.add(copy);
        }
        tableModel.setRows(copies);
    }

    private static @Nullable String lastKeyboardShortcut(Shortcut[] shortcuts) {
        KeyboardShortcut last = null;
        for (Shortcut s : shortcuts) {
            if (s.isKeyboard()) last = (KeyboardShortcut) s;
        }
        return last != null ? last.getFirstKeyStroke().toString() : null;
    }

    @Override
    public void disposeUIResources() {
        // Revert any settings mutations made by ToolbarLauncherKeymapListener during this
        // session (e.g. the user changed a shortcut in the Keymap panel but cancelled).
        // IntelliJ does not fire shortcutsChanged when it reverts the keymap on Cancel,
        // so we cannot rely on the listener to undo itself — we restore the backup instead.
        if (settingsBackup != null) {
            ToolbarLauncherSettings.getInstance().setActions(settingsBackup);
            settingsBackup = null;
        }
        // Revert keymap changes written by the plugin's own edit dialog (updateKeymap).
        revertKeymap();

        // Unregister provisional actions that were added during this session but never
        // committed via Apply. apply() clears the set, so this only runs on Cancel/OK-without-Apply.
        if (!provisionalActionIds.isEmpty()) {
            ActionManager am = ActionManager.getInstance();
            for (String actionId : provisionalActionIds) {
                am.unregisterAction(actionId);
                ActionsRegistrar.keymapBaseline.remove(actionId);
            }
            provisionalActionIds.clear();
        }

        if (messageBusConnection != null) {
            messageBusConnection.disconnect();
            messageBusConnection = null;
        }
        mainPanel           = null;
        table               = null;
        tableModel          = null;
        originalKeymapState = null;
    }

    private static class ActionsTableModel extends AbstractTableModel {
        private final List<ActionConfig> rows = new ArrayList<>();

        void setRows(List<ActionConfig> source) {
            rows.clear();
            for (ActionConfig c : source) rows.add(c.copy());
            fireTableDataChanged();
        }

        List<ActionConfig> getRows()     { return new ArrayList<>(rows); }
        ActionConfig       getRow(int i) { return rows.get(i); }

        void addRow(ActionConfig c) {
            rows.add(c);
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }

        void removeRow(int i) {
            rows.remove(i);
            fireTableRowsDeleted(i, i);
        }

        void moveRow(int from, int to) {
            ActionConfig row = rows.remove(from);
            rows.add(to, row);
            fireTableDataChanged();
        }

        @Override public int getRowCount()    { return rows.size(); }
        @Override public int getColumnCount() { return 6; }

        @Override
        public String getColumnName(int col) {
            return switch (col) {
                case 0, 1 -> "";
                case 2 -> "Type";
                case 3 -> "Label";
                case 4 -> "Command";
                default -> "Shortcut";
            };
        }

        @Override
        public Class<?> getColumnClass(int col) {
            return col == 0 ? Boolean.class : Object.class;
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return col == 0;
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            if (col == 0 && value instanceof Boolean b) {
                rows.get(row).setEnabled(b);
                fireTableCellUpdated(row, col);
            }
        }

        @Override
        public Object getValueAt(int row, int col) {
            ActionConfig c = rows.get(row);
            return switch (col) {
                case 0 -> c.isEnabled();
                case 1 -> ToolbarAction.loadIcon(c.getIconPath());
                case 2 -> ToolType.fromId(c.getCommandType()).displayName;
                case 3 -> c.getLabel();
                case 4 -> c.getGoals();
                default -> {
                    if (c.getShortcut() == null || c.getShortcut().isEmpty()) yield "";
                    KeyStroke ks = KeyStroke.getKeyStroke(c.getShortcut());
                    yield ks != null ? KeymapUtil.getKeystrokeText(ks) : "";
                }
            };
        }
    }
}
