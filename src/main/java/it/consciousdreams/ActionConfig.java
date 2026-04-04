package it.consciousdreams;

import java.util.Objects;

public class ActionConfig {
    public String id;
    public String label;
    public String goals;        // Maven goals or shell/tool command
    public String iconPath   = "/icons/maven_install.svg";
    public String shortcut;     // KeyStroke.toString() format, e.g. "ctrl alt pressed S"
    public String commandType = "maven"; // ToolType id

    public ActionConfig() {}

    public ActionConfig(String id, String label, String goals, String iconPath) {
        this.id = id;
        this.label = label;
        this.goals = goals;
        this.iconPath = iconPath;
    }

    public boolean isMaven() {
        return ToolType.fromId(commandType).isMaven();
    }

    public ActionConfig copy() {
        ActionConfig c = new ActionConfig(id, label, goals, iconPath);
        c.shortcut    = shortcut;
        c.commandType = commandType;
        return c;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActionConfig)) return false;
        ActionConfig that = (ActionConfig) o;
        return Objects.equals(id, that.id)
                && Objects.equals(label, that.label)
                && Objects.equals(goals, that.goals)
                && Objects.equals(iconPath, that.iconPath)
                && Objects.equals(shortcut, that.shortcut)
                && Objects.equals(commandType, that.commandType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, label, goals, iconPath, shortcut, commandType);
    }
}
