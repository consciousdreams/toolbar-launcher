package it.consciousdreams;

import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
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

        // We handle BOTH the active keymap and the Keymap settings panel's editing clone.
        // Rule 3 requires that adding a shortcut on the Keymap panel removes any other
        // keyboard shortcuts for that command *immediately* — i.e. on the clone, before
        // Apply. Cancel safety is provided by ToolbarLauncherConfigurable.settingsBackup,
        // which restores live settings if the user discards the session.
        //
        // NOTE: we intentionally do NOT filter on fromSettings here.
        // Shortcut removal from the Keymap panel fires fromSettings=false in some
        // IntelliJ versions; filtering it out was the reason removals were silently ignored.
        boolean isActive = keymap == KeymapManager.getInstance().getActiveKeymap();

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

            // Use the ActionConfig's current shortcut as the "before this change" reference.
            // It is updated on every event below, so on the Nth reassignment it holds the
            // (N-1)th value — exactly what resolveToKeep needs. keymapBaseline would be
            // stale here for clone events (only updated on active-keymap commits), causing
            // 2nd+ reassignments via the Keymap settings panel to resolve to the previous
            // shortcut instead of the new one.
            String baseline = matched.getShortcut();
            KeyboardShortcut toKeep = resolveToKeep(keymap.getShortcuts(id), baseline);

            String newValue = toKeep != null ? toKeep.getFirstKeyStroke().toString() : null;

            if (isActive) {
                ActionsRegistrar.keymapBaseline.put(id, newValue);
            }

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
