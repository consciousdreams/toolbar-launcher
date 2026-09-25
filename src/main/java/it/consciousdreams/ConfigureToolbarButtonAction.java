package it.consciousdreams;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

/** An item in IntelliJ's existing toolbar context menu, shown for our buttons only. */
public final class ConfigureToolbarButtonAction extends DumbAwareAction {

    public ConfigureToolbarButtonAction() {
        super("Configure...");
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(context(event) != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        ToolbarButtonContextMenu.Context context = context(event);
        if (context == null) return;
        ShowSettingsUtil.getInstance().showSettingsDialog(context.project(), ToolbarLauncherConfigurable.class,
                configurable -> configurable.requestEdit(context.configId()));
    }

    private ToolbarButtonContextMenu.Context context(AnActionEvent event) {
        return ApplicationManager.getApplication().getService(ToolbarButtonContextMenu.class).getContext(event);
    }
}
