package it.consciousdreams;

import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManagerListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class ToolbarLauncherKeymapListener implements KeymapManagerListener {

    @Override
    public void shortcutsChanged(@NotNull Keymap keymap, @NotNull Collection<String> actionIds, boolean fromSettings) {

        if (!fromSettings)
            return;

        for (String id : actionIds) {
            if (id.startsWith(ActionsRegistrar.PREFIX)) {
                Shortcut lastShortcut = lastKeyboardShortcut(keymap.getShortcuts(id));
                if (lastShortcut != null) {
                    for (Shortcut shortcut : keymap.getShortcuts(id)) {
                        if (!shortcut.equals(lastShortcut) && shortcut.isKeyboard()) {
                            keymap.removeShortcut(id, shortcut);
                        }
                    }
                }

                List<ActionConfig> configs = ToolbarLauncherSettings.getInstance().getActions();
                for (ActionConfig config : configs) {
                    String idConfig = ActionsRegistrar.PREFIX + config.getId();
                    if (idConfig.equals(id)) {
                        config.setShortcut(lastShortcut != null ? ((KeyboardShortcut) lastShortcut).getFirstKeyStroke().toString() : null);
                        break;
                    }
                }
            }
        }
        // ActionsRegistrar.sync();
    }

    private static @Nullable Shortcut lastKeyboardShortcut(Shortcut[] shortcuts) {
        KeyboardShortcut last = null;
        for (Shortcut s : shortcuts) {
            if (s.isKeyboard()) last = (KeyboardShortcut) s;
        }
        return last;
    }

/*
    @Override
    public void activeKeymapChanged(@Nullable Keymap keymap) {
        LOG.warn("Active keymap changed. New active keymap: "
                + (keymap != null ? keymap.getName() : "null"));
        if (keymap != null) {
            syncAllShortcuts(keymap);
        }
    }

    private void syncAllShortcuts(Keymap keymap) {
        List<ActionConfig> configs = ToolbarLauncherSettings.getInstance().getActions();
        for (ActionConfig config : configs) {
            String actionId = ActionsRegistrar.PREFIX + config.getId();
            Shortcut[] shortcuts = keymap.getShortcuts(actionId);
            LOG.warn("Sync – action: " + actionId + " shortcuts: " + Arrays.toString(shortcuts));
            Shortcut lastShortcut = lastKeyboardShortcut(shortcuts);
            config.setShortcut(lastShortcut != null ? ((KeyboardShortcut) lastShortcut).getFirstKeyStroke().toString() : null);
        }
    }

    private List<Shortcut> getNotInCommonShortcut(String id, Keymap oldKeymap, Keymap newKeymap) {
        Set<Shortcut> oldSet = new HashSet<>(Arrays.asList(oldKeymap.getShortcuts(id)));
        Set<Shortcut> newSet = new HashSet<>(Arrays.asList(newKeymap.getShortcuts(id)));

        return Stream.concat(
                oldSet.stream().filter(s -> !newSet.contains(s)),
                newSet.stream().filter(s -> !oldSet.contains(s))
        ).toList();
    }

    private boolean isKeyboardShortcut(List<Shortcut> shortcuts) {
        for (Shortcut shortcut : shortcuts) {
            if (shortcut.isKeyboard()) return true;
        }
        return false;
    }
*/

}
