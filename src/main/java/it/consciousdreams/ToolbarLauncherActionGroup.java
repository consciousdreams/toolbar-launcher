package it.consciousdreams;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class ToolbarLauncherActionGroup extends ActionGroup {

    public ToolbarLauncherActionGroup() {
        super();
        setPopup(false);
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent ignored) {
        ActionManager am = ActionManager.getInstance();
        return ToolbarLauncherSettings.getInstance().getActions().stream()
                .filter(ActionConfig::isEnabled)
                .map(config -> am.getAction(ActionsRegistrar.PREFIX + config.getId()))
                .filter(Objects::nonNull)
                .toArray(AnAction[]::new);
    }
}
