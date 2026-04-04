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
        public List<ActionConfig> actions = new ArrayList<>();
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
                "/icons/maven_install.svg"
        );
        skipTests.shortcut = modifier + " pressed S";
        s.actions.add(skipTests);

        ActionConfig withTests = new ActionConfig(
                UUID.randomUUID().toString(),
                "Maven Clean Install",
                "clean install",
                "/icons/maven_install_with_tests.svg"
        );
        withTests.shortcut = modifier + " pressed M";
        s.actions.add(withTests);

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
        return state.actions;
    }

    public void setActions(List<ActionConfig> actions) {
        state.actions = new ArrayList<>(actions);
    }
}
