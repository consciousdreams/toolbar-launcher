package it.consciousdreams;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.keymap.KeymapExtension;
import com.intellij.openapi.keymap.KeymapGroup;
import com.intellij.openapi.keymap.KeymapGroupFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.Nullable;

public class ToolbarLauncherKeymapExtension implements KeymapExtension {

    @Override
    public @Nullable KeymapGroup createGroup(Condition<? super AnAction> filtered, @Nullable Project project) {
        ActionManager am = ActionManager.getInstance();
        KeymapGroup group = KeymapGroupFactory.getInstance().createGroup("Toolbar Launcher");
        int added = 0;

        for (ActionConfig config : ToolbarLauncherSettings.getInstance().getActions()) {
            String id = ActionsRegistrar.PREFIX + config.getId();
            AnAction action = am.getAction(id);
            if (action != null && (filtered == null || filtered.value(action))) {
                group.addActionId(id);
                added++;
            }
        }

        return added > 0 ? group : null;
    }
}