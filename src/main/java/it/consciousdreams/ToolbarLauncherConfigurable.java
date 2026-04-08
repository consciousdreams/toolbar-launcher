package it.consciousdreams;

import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapManagerListener;
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
import java.awt.event.MouseAdapter;
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

        subscribeToKeymapChanges();
        reset();
        return mainPanel;
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
            public void shortcutsChanged(@NotNull Keymap keymap, @NotNull java.util.Collection<String> actionIds, boolean fromSettings) {
                if (actionIds.stream().anyMatch(id -> id.startsWith(ActionsRegistrar.PREFIX))) reset();
            }

            @Override
            public void activeKeymapChanged(@Nullable Keymap keymap) {
                reset();
            }
        });
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
        ToolbarLauncherSettings.getInstance().setActions(tableModel.getRows());
        ActionsRegistrar.sync();
        // Fire activeKeymapChanged so the Keymap settings panel rebuilds its action tree
        ApplicationManager.getApplication().getMessageBus()
                .syncPublisher(KeymapManagerListener.TOPIC)
                .activeKeymapChanged(KeymapManager.getInstance().getActiveKeymap());
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
