package it.consciousdreams;

import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapManagerListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Application-level listener that logs whenever a user changes a shortcut
 * for any Toolbar Launcher action.
 */
public class ToolbarLauncherKeymapListener implements KeymapManagerListener {

    private static final Logger LOG = Logger.getInstance(ToolbarLauncherKeymapListener.class);

    @Override
    public void shortcutsChanged(@NotNull Keymap keymap, @NotNull Collection<String> actionIds, boolean fromSettings) {
        for (String id : actionIds) {
            if (id.startsWith(ActionsRegistrar.PREFIX)) {
                Keymap oldKeymap = KeymapManager.getInstance().getActiveKeymap();
                LOG.warn("Previous shortcut for action: " + id + " on keymap: " + oldKeymap.getName() + " shortcuts: " + Arrays.toString(oldKeymap.getShortcuts(id)));
                LOG.warn("New Shortcut for action: " + id + " on keymap: " + keymap.getName() + " shortcuts: " + Arrays.toString(keymap.getShortcuts(id)));
/*
                List<Shortcut> notInCommon = getNotInCommonShortcut(id, oldKeymap, keymap);
                LOG.warn("Shortcuts not in common for action " + id + ": " + notInCommon);

                boolean removed = !isKeyboardShortcut(notInCommon);
*/
                Shortcut lastShortcut = lastKeyboardShortcut(keymap.getShortcuts(id));
                if (lastShortcut != null) {
                    for (Shortcut shortcut : keymap.getShortcuts(id)) {
                        if (!shortcut.equals(lastShortcut) && shortcut.isKeyboard()) {
                            keymap.removeShortcut(id, shortcut);
                        }
                    }
                }
                LOG.warn("SET PLUGIN " + lastShortcut);

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
    }

    private static @Nullable Shortcut lastKeyboardShortcut(Shortcut[] shortcuts) {
        KeyboardShortcut last = null;
        for (Shortcut s : shortcuts) {
            if (s.isKeyboard()) last = (KeyboardShortcut) s;
        }
        return last;
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

}
