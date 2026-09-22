package it.consciousdreams;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loaded only through the optional Maven descriptor. */
public final class NativeMavenCommandRunner implements MavenCommandRunner {
    @Override
    public void run(Project project, ActionConfig config) {
        MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(project);
        if (!mavenProjectsManager.isMavenizedProject()) {
            Messages.showWarningDialog(project, "This is not a Maven project.", config.getLabel());
            return;
        }

        List<String> goals = new ArrayList<>();
        Map<String, String> props = new LinkedHashMap<>();

        for (String token : config.getGoals().trim().split("\\s+")) {
            if (token.startsWith("-D")) {
                String kv = token.substring(2);
                int eq = kv.indexOf('=');
                if (eq >= 0) {
                    props.put(kv.substring(0, eq), kv.substring(eq + 1));
                } else {
                    props.put(kv, "true");
                }
            } else if (!token.isEmpty()) {
                goals.add(token);
            }
        }

        MavenRunnerParameters params = new MavenRunnerParameters(
                true,
                project.getBasePath(),
                (String) null,
                goals,
                Collections.emptyList()
        );

        MavenRunnerSettings settings = MavenRunner.getInstance(project).getSettings().clone();
        if (!props.isEmpty()) {
            Map<String, String> merged = new LinkedHashMap<>(settings.getMavenProperties());
            merged.putAll(props);
            settings.setMavenProperties(merged);
        }

        MavenRunner.getInstance(project).run(params, settings, null);
    }
}
