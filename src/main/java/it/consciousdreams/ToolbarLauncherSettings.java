package it.consciousdreams;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@State(name = "ToolbarLauncherSettings", storages = @Storage("ToolbarLauncher.xml"))
@Service(Service.Level.APP)
public final class ToolbarLauncherSettings implements PersistentStateComponent<ToolbarLauncherSettings.State> {

    public static class State {
        private List<ActionConfig> actions = new ArrayList<>();

        public List<ActionConfig> getActions()                    { return actions; }
        public void               setActions(List<ActionConfig> a) { actions = a; }
    }

    private State state = createDefaultState();

    public static ToolbarLauncherSettings getInstance() {
        return ApplicationManager.getApplication().getService(ToolbarLauncherSettings.class);
    }

    private static State createDefaultState() {
        State s = new State();
        boolean mac = System.getProperty("os.name", "").toLowerCase().contains("mac");
        String modifier = mac ? "meta alt" : "ctrl alt";

        ActionConfig skipTests = new ActionConfig(
                UUID.randomUUID().toString(),
                "Maven Clean Install (skip tests)",
                "clean install -Dmaven.test.skip=true",
                ActionEditDialog.DEFAULT_ICON_PATH
        );
        skipTests.setShortcut(modifier + " pressed S");
        s.getActions().add(skipTests);

        ActionConfig withTests = new ActionConfig(
                UUID.randomUUID().toString(),
                "Maven Clean Install",
                "clean install",
                ActionEditDialog.TESTS_ICON_PATH
        );
        withTests.setShortcut(modifier + " pressed M");
        s.getActions().add(withTests);

        return s;
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public List<ActionConfig> getActions() {
        return state.getActions();
    }

    public void setActions(List<ActionConfig> actions) {
        state.setActions(new ArrayList<>(actions));
    }
}
