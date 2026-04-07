package it.consciousdreams;

import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.Configurable;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.KeyStroke;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ToolbarLauncherConfigurable implements Configurable {

    private JPanel mainPanel;
    private JBTable table;
    private ActionsTableModel tableModel;
    private MessageBusConnection messageBusConnection;

    @Override
    public @Nls String getDisplayName() {
        return "Toolbar Launcher";
    }

    @Override
    public @Nullable JComponent createComponent() {
        tableModel = new ActionsTableModel();
        table = new JBTable(tableModel) {
            @Override
            public String getToolTipText(@NotNull MouseEvent e) {
                int col = columnAtPoint(e.getPoint());
                if (col != 0) return super.getToolTipText(e);
                int row = rowAtPoint(e.getPoint());
                if (row < 0) return null;
                boolean enabled = tableModel.getRow(row).isEnabled();
                return enabled ? "Click to disable this button" : "Click to enable this button";
            }
        };
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(22);

        // Enabled column (checkbox)
        table.getColumnModel().getColumn(0).setMinWidth(28);
        table.getColumnModel().getColumn(0).setMaxWidth(28);
        // Icon column
        table.getColumnModel().getColumn(1).setMinWidth(28);
        table.getColumnModel().getColumn(1).setMaxWidth(28);
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
        // Type column
        table.getColumnModel().getColumn(2).setMinWidth(55);
        table.getColumnModel().getColumn(2).setMaxWidth(55);
        table.getColumnModel().getColumn(3).setPreferredWidth(160);
        table.getColumnModel().getColumn(4).setPreferredWidth(230);
        table.getColumnModel().getColumn(5).setPreferredWidth(100);

        JPanel decoratedTable = ToolbarDecorator.createDecorator(table)
                .setAddAction(button -> addAction())
                .setRemoveAction(button -> removeAction())
                .setEditAction(button -> editAction())
                .createPanel();

        mainPanel = new JPanel(new BorderLayout(0, 8));
        mainPanel.add(new JLabel("Configure toolbar buttons:"), BorderLayout.NORTH);
        mainPanel.add(decoratedTable, BorderLayout.CENTER);

        messageBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
        messageBusConnection.subscribe(KeymapManagerListener.TOPIC, new KeymapManagerListener() {
            @Override
            public void shortcutsChanged(@NotNull Keymap keymap, @NotNull java.util.Collection<String> actionIds, boolean fromSettings) {
                for (String id : actionIds) {
                    if (id.startsWith(ActionsRegistrar.PREFIX)) {
                        reset();
                        break;
                    }
                }
            }

            @Override
            public void activeKeymapChanged(@Nullable Keymap keymap) {
                reset();
            }
        });

        reset();
        return mainPanel;
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
        }
    }

    private void editAction() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        ActionConfig config = tableModel.getRow(row);
        ActionEditDialog dialog = new ActionEditDialog(
                config.getLabel(), config.getGoals(), config.getIconPath(), config.getShortcut(), config.getCommandType());
        if (dialog.showAndGet()) {
            config.setLabel(dialog.getLabel());
            config.setGoals(dialog.getGoals());
            config.setIconPath(dialog.getIconPath());
            config.setShortcut(dialog.getShortcut());
            config.setCommandType(dialog.getCommandType());
            tableModel.fireTableRowsUpdated(row, row);
        }
    }

    private void removeAction() {
        int row = table.getSelectedRow();
        if (row >= 0) tableModel.removeRow(row);
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
        ToolbarLauncherSettings.getInstance().setActions(tableModel.getRows());
        ActionsRegistrar.sync();
        // Re-set the active keymap to fire activeKeymapChanged, so the Keymap settings panel refreshes
        KeymapManagerEx km = KeymapManagerEx.getInstanceEx();
        km.setActiveKeymap(km.getActiveKeymap());
    }

    @Override
    public void reset() {
        if (tableModel == null) return;
        Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
        List<ActionConfig> copies = new ArrayList<>();
        for (ActionConfig config : ToolbarLauncherSettings.getInstance().getActions()) {
            ActionConfig copy = config.copy();
            if (copy.isEnabled()) {
                // Read the shortcut from the keymap — user may have changed it via Settings → Keymap
                String actionId = ActionsRegistrar.PREFIX + copy.getId();
                Shortcut[] shortcuts = keymap.getShortcuts(actionId);
                if (shortcuts.length > 0 && shortcuts[shortcuts.length - 1] instanceof KeyboardShortcut kbs) {
                    copy.setShortcut(kbs.getFirstKeyStroke().toString());
                } else {
                    copy.setShortcut(null);
                }
            }
            copies.add(copy);
        }
        tableModel.setRows(copies);
    }

    @Override
    public void disposeUIResources() {
        if (messageBusConnection != null) {
            messageBusConnection.disconnect();
            messageBusConnection = null;
        }
        mainPanel  = null;
        table      = null;
        tableModel = null;
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
