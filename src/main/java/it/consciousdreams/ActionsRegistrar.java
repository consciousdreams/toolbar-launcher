package it.consciousdreams;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.ui.customization.CustomActionsListener;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.WindowManager;

import java.awt.Component;
import java.awt.Container;
import org.jetbrains.annotations.NotNull;

import javax.swing.KeyStroke;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jdom.Element;

public class ActionsRegistrar implements AppLifecycleListener, DynamicPluginListener {

    static final String PREFIX = "it.consciousdreams.toolbarlauncher.";
    private static final String PLUGIN_ID = "it.consciousdreams.toolbar-launcher";

    private static final String ACTION_TYPE = "action_type";
    private static final String GROUP = "group";
    private static final String VALUE = "value";
    private static final String ACTION_TYPE_ADD = "1";
    private static final String ACTION_TYPE_REMOVE = "-1";

    /** Tracks IDs we have registered so we can unregister without querying ActionManager by prefix. */
    private static final Set<String> registeredIds = new HashSet<>();

    /** Guards against re-entry when we modify the schema from inside the listener. */
    private static boolean handlingCustomization = false;

    /**
     * Set to {@code true} while {@link it.consciousdreams.ToolbarLauncherConfigurable#updateKeymap}
     * is running, so {@link ToolbarLauncherKeymapListener} ignores the keymap events it fires.
     */
    static boolean updatingKeymapFromPlugin = false;

    /**
     * Tracks the shortcut currently present in the active keymap for each of our actions.
     * Updated by {@link ToolbarLauncherConfigurable#updateKeymap} and by
     * {@link ToolbarLauncherKeymapListener} so the listener always has an accurate baseline
     * for {@code resolveToKeep}, independent of when the user clicks Apply.
     */
    static final Map<String, String> keymapBaseline = new HashMap<>();

    @Override
    public void appFrameCreated(@NotNull List<String> ignoredArgs) {
        sync();
        CustomActionsListener.subscribe(Disposer.newDisposable(), ActionsRegistrar::handleToolbarCustomization);
        ApplicationManager.getApplication().getMessageBus().connect().subscribe(KeymapManagerListener.TOPIC, new ToolbarLauncherKeymapListener());
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
                    // Apply from config only when the keymap has no shortcut yet (e.g. fresh install).
                    // If the keymap already has one, the user set it intentionally — leave it alone.
                    if (keymap.getShortcuts(id).length == 0) {
                        applyShortcut(keymap, id, config.getShortcut());
                    }
                }
            }
        }
        // Refresh keymapBaseline to reflect the current active keymap state.
        keymapBaseline.clear();
        for (ActionConfig config : configs) {
            if (config.isEnabled()) {
                String actionId = PREFIX + config.getId();
                String shortcut = null;
                for (com.intellij.openapi.actionSystem.Shortcut s : keymap.getShortcuts(actionId)) {
                    if (s.isKeyboard())
                        shortcut = ((com.intellij.openapi.actionSystem.KeyboardShortcut) s).getFirstKeyStroke().toString();
                }
                keymapBaseline.put(actionId, shortcut);
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
     * Reads the schema XML via the public {@link CustomActionsSchema#getState()} API
     * to detect DELETED entries for our actions, disables the corresponding configs,
     * removes the stale entries via {@link CustomActionsSchema#loadState}, and syncs.
     */
    private static void handleToolbarCustomization() {
        if (handlingCustomization) return;
        handlingCustomization = true;
        try {
            CustomActionsSchema schema = CustomActionsSchema.getInstance();
            Element state = schema.getState();

            List<Element> groups = state.getChildren(GROUP);

            // Collect our action IDs that have ADDED entries (i.e. moved, not truly removed)
            Set<String> addedIds = new HashSet<>();
            for (Element group : groups) {
                if (ACTION_TYPE_ADD.equals(group.getAttributeValue(ACTION_TYPE))) {
                    String value = group.getAttributeValue(VALUE);
                    if (value != null && value.startsWith(PREFIX)) {
                        addedIds.add(value);
                    }
                }
            }

            List<ActionConfig> configs = ToolbarLauncherSettings.getInstance().getActions();
            boolean changed = false;
            Set<String> removedIds = new HashSet<>();

            for (Element group : groups) {
                if (!ACTION_TYPE_REMOVE.equals(group.getAttributeValue(ACTION_TYPE))) continue;
                String value = group.getAttributeValue(VALUE);
                if (value == null || !value.startsWith(PREFIX)) continue;
                // DELETED + ADDED for the same ID means a move, not a removal
                if (addedIds.contains(value)) continue;

                for (ActionConfig config : configs) {
                    if ((PREFIX + config.getId()).equals(value) && config.isEnabled()) {
                        config.setEnabled(false);
                        changed = true;
                        removedIds.add(value);
                    }
                }
            }

            if (changed) {
                // Rebuild state without our DELETED entries so re-enabling works
                Element newState = new Element(state.getName());
                for (Element child : state.getChildren()) {
                    boolean isOurDeletedEntry = GROUP.equals(child.getName())
                            && ACTION_TYPE_REMOVE.equals(child.getAttributeValue(ACTION_TYPE))
                            && removedIds.contains(child.getAttributeValue(VALUE));
                    if (!isOurDeletedEntry) {
                        newState.addContent(child.clone());
                    }
                }
                schema.loadState(newState);

                sync();
            }
        } finally {
            handlingCustomization = false;
        }
    }
}
