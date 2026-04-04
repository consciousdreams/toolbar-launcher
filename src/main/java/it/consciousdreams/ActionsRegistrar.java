package it.consciousdreams;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.KeyStroke;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ActionsRegistrar implements AppLifecycleListener {

    static final String PREFIX = "it.consciousdreams.toolbarlauncher.";

    /** Tracks IDs we have registered so we can unregister without querying ActionManager by prefix. */
    private static final Set<String> registeredIds = new HashSet<>();

    @Override
    public void appFrameCreated(@NotNull List<String> ignoredArgs) {
        sync();
    }

    /**
     * Registers/unregisters ToolbarActions in ActionManager to match current settings.
     * Safe to call multiple times (idempotent for unchanged actions).
     */
    static void sync() {
        ActionManager am = ActionManager.getInstance();
        List<ActionConfig> configs = ToolbarLauncherSettings.getInstance().getActions();

        Set<String> expectedIds = configs.stream()
                .filter(ActionConfig::isEnabled)
                .map(c -> PREFIX + c.getId())
                .collect(Collectors.toSet());

        // Unregister actions no longer in settings
        Set<String> toRemove = new HashSet<>(registeredIds);
        toRemove.removeAll(expectedIds);
        for (String id : toRemove) {
            am.unregisterAction(id);
            registeredIds.remove(id);
        }

        // Register or refresh each enabled action
        Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
        for (ActionConfig config : configs) {
            if (config.isEnabled()) {
                String id = PREFIX + config.getId();
                AnAction existing = am.getAction(id);
                if (existing instanceof ToolbarAction ta && ta.getConfig().equals(config)) {
                    registeredIds.add(id);
                } else {
                    if (existing != null) {
                        am.unregisterAction(id);
                    }
                    am.registerAction(id, new ToolbarAction(config));
                    registeredIds.add(id);
                    applyShortcut(keymap, id, config.getShortcut());
                }
            }
        }
    }

    private static void applyShortcut(Keymap keymap, String actionId,
                                      @org.jetbrains.annotations.Nullable String shortcut) {
        keymap.removeAllActionShortcuts(actionId);
        if (shortcut != null && !shortcut.isEmpty()) {
            KeyStroke ks = KeyStroke.getKeyStroke(shortcut);
            if (ks != null) {
                keymap.addShortcut(actionId, new KeyboardShortcut(ks, null));
            }
        }
    }
}
