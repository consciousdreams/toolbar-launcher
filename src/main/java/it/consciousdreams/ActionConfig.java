package it.consciousdreams;

import java.util.Objects;

public class ActionConfig {
    private String  id;
    private String  label;
    private String  goals;
    private String  iconPath    = ActionEditDialog.DEFAULT_ICON_PATH;
    private String  commandType = "maven";
    private boolean enabled     = true;

    public ActionConfig() {}

    public ActionConfig(String id, String label, String goals, String iconPath) {
        this.id       = id;
        this.label    = label;
        this.goals    = goals;
        this.iconPath = iconPath;
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public String  getId()          { return id; }
    public void    setId(String id) { this.id = id; }

    public String  getLabel()             { return label; }
    public void    setLabel(String label) { this.label = label; }

    public String  getGoals()             { return goals; }
    public void    setGoals(String goals) { this.goals = goals; }

    public String  getIconPath()                { return iconPath; }
    public void    setIconPath(String iconPath) { this.iconPath = iconPath; }

    public String  getCommandType()                   { return commandType; }
    public void    setCommandType(String commandType) { this.commandType = commandType; }

    public boolean isEnabled()                { return enabled; }
    public void    setEnabled(boolean enabled) { this.enabled = enabled; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public boolean isMaven() {
        return ToolType.fromId(commandType).isMaven();
    }

    public ActionConfig copy() {
        ActionConfig c = new ActionConfig(id, label, goals, iconPath);
        c.commandType = commandType;
        c.enabled     = enabled;
        return c;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActionConfig that)) return false;
        return enabled == that.enabled
                && Objects.equals(id, that.id)
                && Objects.equals(label, that.label)
                && Objects.equals(goals, that.goals)
                && Objects.equals(iconPath, that.iconPath)
                && Objects.equals(commandType, that.commandType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, label, goals, iconPath, commandType, enabled);
    }
}
