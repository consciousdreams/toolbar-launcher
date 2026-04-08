package it.consciousdreams;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.ui.customization.ActionUrl;
import com.intellij.ide.ui.customization.CustomActionsListener;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.WindowManager;

import java.awt.Component;
import java.awt.Container;
import org.jetbrains.annotations.NotNull;

import javax.swing.KeyStroke;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ActionsRegistrar implements AppLifecycleListener, DynamicPluginListener {

    static final String PREFIX = "it.consciousdreams.toolbarlauncher.";
    private static final String PLUGIN_ID = "it.consciousdreams.toolbar-launcher";

    /** Tracks IDs we have registered so we can unregister without querying ActionManager by prefix. */
    private static final Set<String> registeredIds = new HashSet<>();

    /** Guards against re-entry when we modify the schema from inside the listener. */
    private static boolean handlingCustomization = false;

    @Override
    public void appFrameCreated(@NotNull List<String> ignoredArgs) {
        sync();
        CustomActionsListener.subscribe(Disposer.newDisposable(), ActionsRegistrar::handleToolbarCustomization);
    }

    @Override
    public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
        if (PLUGIN_ID.equals(pluginDescriptor.getPluginId().getIdString())) {
            sync();
        }
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

        // Unregister actions no longer in settings and remove their shortcuts from the keymap
        Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
        Set<String> toRemove = new HashSet<>(registeredIds);
        toRemove.removeAll(expectedIds);
        for (String id : toRemove) {
            keymap.removeAllActionShortcuts(id);
            am.unregisterAction(id);
            registeredIds.remove(id);
        }

        // Register or refresh each enabled action
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
        var frame = WindowManager.getInstance().getIdeFrame(null);
        if (frame != null) updateToolbars(frame.getComponent());
    }

    private static void updateToolbars(Component component) {
        if (component instanceof ActionToolbar toolbar) toolbar.updateActionsAsync();
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) updateToolbars(child);
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

    /**
     * Called when the user modifies the toolbar via "Customize Toolbar".
     * If any of our actions were removed, disable them in our settings and
     * clean up the schema entry so re-enabling from our settings works later.
     */
    private static void handleToolbarCustomization() {
        if (handlingCustomization) return;
        handlingCustomization = true;
        try {
            CustomActionsSchema schema = CustomActionsSchema.getInstance();
            List<ActionUrl> urls = schema.getActions();

            // Collect our action IDs that have ADDED entries (i.e. moved, not truly removed)
            Set<String> addedIds = new HashSet<>();
            for (ActionUrl url : urls) {
                if (url.getActionType() == ActionUrl.ADDED
                        && url.getComponent() instanceof String id
                        && id.startsWith(PREFIX)) {
                    addedIds.add(id);
                }
            }

            List<ActionConfig> configs = ToolbarLauncherSettings.getInstance().getActions();
            boolean changed = false;
            List<ActionUrl> toRemove = new ArrayList<>();

            for (ActionUrl url : urls) {
                if (url.getActionType() != ActionUrl.DELETED) continue;
                Object component = url.getComponent();
                if (!(component instanceof String actionId)) continue;
                if (!actionId.startsWith(PREFIX)) continue;
                // DELETED + ADDED for the same ID means a move, not a removal
                if (addedIds.contains(actionId)) continue;

                for (ActionConfig config : configs) {
                    if ((PREFIX + config.getId()).equals(actionId) && config.isEnabled()) {
                        config.setEnabled(false);
                        changed = true;
                        toRemove.add(url);
                    }
                }
            }

            if (changed) {
                // Remove DELETED entries from schema so re-enabling from our settings works
                List<ActionUrl> updatedUrls = new ArrayList<>(urls);
                updatedUrls.removeAll(toRemove);
                schema.setActions(updatedUrls);

                sync();
            }
        } finally {
            handlingCustomization = false;
        }
    }
}
