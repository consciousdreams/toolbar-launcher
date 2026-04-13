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

            // Identify the shortcut to keep. Prefer the one that differs from the stored
            // value: IntelliJ fires this event while both old and new are simultaneously
            // present, so picking "last by position" is unreliable.
            KeyboardShortcut toKeep = resolveToKeep(keymap.getShortcuts(id), matched.getShortcut());

            // Update config BEFORE removing extras so the re-entrant shortcutsChanged
            // call triggered by keymap.removeShortcut below sees the updated value and
            // exits cleanly with no further work to do.
            String newValue = toKeep != null ? toKeep.getFirstKeyStroke().toString() : null;
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
