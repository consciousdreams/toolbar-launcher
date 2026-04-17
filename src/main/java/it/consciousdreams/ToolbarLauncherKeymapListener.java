package it.consciousdreams;

import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManagerListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class ToolbarLauncherKeymapListener implements KeymapManagerListener {

    /**
     * Guards against re-entry when our own {@code keymap.removeShortcut} call
     * (used to strip extra shortcuts) fires a new {@code shortcutsChanged} event.
     */
    private static boolean handlingChange = false;

    @Override
    public void shortcutsChanged(@NotNull Keymap keymap, @NotNull Collection<String> actionIds, boolean fromSettings) {
        // Re-entry guard: skip events we ourselves caused via keymap.removeShortcut.
        if (handlingChange) return;
        // Plugin-update guard: skip events fired by updateKeymap in the configurable.
        if (ActionsRegistrar.updatingKeymapFromPlugin) return;

        // NOTE: we intentionally do NOT filter on fromSettings here.
        // Shortcut removal from the Keymap panel fires fromSettings=false in some
        // IntelliJ versions; filtering it out was the reason removals were silently ignored.

        List<ActionConfig> configs = ToolbarLauncherSettings.getInstance().getActions();

        for (String id : actionIds) {
            if (!id.startsWith(ActionsRegistrar.PREFIX)) continue;

            ActionConfig matched = null;
            for (ActionConfig config : configs) {
                if ((ActionsRegistrar.PREFIX + config.getId()).equals(id)) {
                    matched = config;
                    break;
                }
            }
            if (matched == null) continue;

            // Use keymapBaseline as the "before this change" reference so that
            // resolveToKeep correctly identifies the newly-assigned shortcut even when
            // the user changed it via the plugin dialog without clicking Apply first
            // (in that case ToolbarLauncherSettings still has the pre-dialog value).
            String baseline = ActionsRegistrar.keymapBaseline.getOrDefault(id, matched.getShortcut());
            KeyboardShortcut toKeep = resolveToKeep(keymap.getShortcuts(id), baseline);

            String newValue = toKeep != null ? toKeep.getFirstKeyStroke().toString() : null;

            // Update baseline BEFORE removing extras so re-entrant shortcutsChanged calls
            // triggered by keymap.removeShortcut below find no further work to do.
            ActionsRegistrar.keymapBaseline.put(id, newValue);

            if (!Objects.equals(matched.getShortcut(), newValue)) {
                matched.setShortcut(newValue);
            }

            // Remove all other keyboard shortcuts, keeping only toKeep.
            if (toKeep != null) {
                handlingChange = true;
                try {
                    for (Shortcut s : keymap.getShortcuts(id)) {
                        if (s.isKeyboard() && !s.equals(toKeep)) {
                            keymap.removeShortcut(id, s);
                        }
                    }
                } finally {
                    handlingChange = false;
                }
            }
        }
    }

    /**
     * Returns the keyboard shortcut to retain after a keymap change.
     * The first shortcut whose keystroke differs from {@code currentValue} is the
     * newly assigned one. Falls back to the first keyboard shortcut found when
     * nothing new was detected (e.g. a redundant event). Returns {@code null} when
     * no keyboard shortcuts remain — the user removed the shortcut entirely.
     */
    private static @Nullable KeyboardShortcut resolveToKeep(Shortcut[] shortcuts, @Nullable String currentValue) {
        KeyboardShortcut fallback = null;
        for (Shortcut s : shortcuts) {
            if (!s.isKeyboard()) continue;
            KeyboardShortcut ks = (KeyboardShortcut) s;
            if (!ks.getFirstKeyStroke().toString().equals(currentValue)) return ks;
            if (fallback == null) fallback = ks;
        }
        return fallback;
    }
}
