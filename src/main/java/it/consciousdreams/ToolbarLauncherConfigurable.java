package it.consciousdreams;

import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.Configurable;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.KeyStroke;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ToolbarLauncherConfigurable implements Configurable {

    private JPanel mainPanel;
    private JBTable table;
    private ActionsTableModel tableModel;

    @Override
    public @Nls String getDisplayName() {
        return "Toolbar Launcher";
    }

    @Override
    public @Nullable JComponent createComponent() {
        tableModel = new ActionsTableModel();
        table = new JBTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(22);

        // Icon column
        table.getColumnModel().getColumn(0).setMinWidth(28);
        table.getColumnModel().getColumn(0).setMaxWidth(28);
        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
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
        table.getColumnModel().getColumn(1).setMinWidth(55);
        table.getColumnModel().getColumn(1).setMaxWidth(55);
        table.getColumnModel().getColumn(2).setPreferredWidth(160);
        table.getColumnModel().getColumn(3).setPreferredWidth(230);
        table.getColumnModel().getColumn(4).setPreferredWidth(100);

        JPanel decoratedTable = ToolbarDecorator.createDecorator(table)
                .setAddAction(button -> addAction())
                .setRemoveAction(button -> removeAction())
                .setEditAction(button -> editAction())
                .createPanel();

        mainPanel = new JPanel(new BorderLayout(0, 8));
        mainPanel.add(new JLabel("Configure toolbar buttons:"), BorderLayout.NORTH);
        mainPanel.add(decoratedTable, BorderLayout.CENTER);

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
            config.shortcut    = dialog.getShortcut();
            config.commandType = dialog.getCommandType();
            tableModel.addRow(config);
        }
    }

    private void editAction() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        ActionConfig config = tableModel.getRow(row);
        ActionEditDialog dialog = new ActionEditDialog(
                config.label, config.goals, config.iconPath, config.shortcut, config.commandType);
        if (dialog.showAndGet()) {
            config.label       = dialog.getLabel();
            config.goals       = dialog.getGoals();
            config.iconPath    = dialog.getIconPath();
            config.shortcut    = dialog.getShortcut();
            config.commandType = dialog.getCommandType();
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
    }

    @Override
    public void reset() {
        if (tableModel == null) return;
        tableModel.setRows(ToolbarLauncherSettings.getInstance().getActions());
    }

    @Override
    public void disposeUIResources() {
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
        @Override public int getColumnCount() { return 5; }

        @Override
        public String getColumnName(int col) {
            return switch (col) {
                case 0 -> "";
                case 1 -> "Type";
                case 2 -> "Label";
                case 3 -> "Command";
                default -> "Shortcut";
            };
        }

        @Override
        public Object getValueAt(int row, int col) {
            ActionConfig c = rows.get(row);
            return switch (col) {
                case 0 -> ToolbarAction.loadIcon(c.iconPath);
                case 1 -> ToolType.fromId(c.commandType).displayName;
                case 2 -> c.label;
                case 3 -> c.goals;
                default -> {
                    if (c.shortcut == null || c.shortcut.isEmpty()) yield "";
                    KeyStroke ks = KeyStroke.getKeyStroke(c.shortcut);
                    yield ks != null ? KeymapUtil.getKeystrokeText(ks) : "";
                }
            };
        }
    }
}
