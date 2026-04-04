package it.consciousdreams;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ToolbarLauncherActionGroup extends ActionGroup {

    public ToolbarLauncherActionGroup() {
        super();
        setPopup(false);
    }

    @Override
    public @NotNull AnAction[] getChildren(@Nullable AnActionEvent e) {
        ActionManager am = ActionManager.getInstance();
        return ToolbarLauncherSettings.getInstance().getActions().stream()
                .map(config -> {
                    String id = ActionsRegistrar.PREFIX + config.id;
                    AnAction action = am.getAction(id);
                    if (action == null) {
                        action = new ToolbarAction(config);
                        am.registerAction(id, action);
                    }
                    return action;
                })
                .toArray(AnAction[]::new);
    }
}
