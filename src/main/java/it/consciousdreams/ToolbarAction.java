package it.consciousdreams;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ToolbarAction extends AnAction {

    private static final Icon FALLBACK_ICON = IconLoader.getIcon(ActionEditDialog.DEFAULT_ICON_PATH, ToolbarAction.class);

    private final ActionConfig config;

    ActionConfig getConfig() { return config; }

    public ToolbarAction(ActionConfig config) {
        super(config.getLabel(), config.getLabel(), loadIcon(config.getIconPath()));
        this.config = config;
    }

    // ── Icon loading ──────────────────────────────────────────────────────────

    static Icon loadIcon(String path) {
        if (path == null || path.isEmpty()) return FALLBACK_ICON;
        try {
            Icon icon;
            if (path.startsWith("/icons/")) {
                icon = IconLoader.getIcon(path, ToolbarAction.class);
            } else {
                URL url = new File(path).toURI().toURL();
                icon = IconLoader.findIcon(url);
                if (icon == null) return FALLBACK_ICON;
            }
            return scaleToToolbarSize(icon);
        } catch (Exception ex) {
            return FALLBACK_ICON;
        }
    }

    private static Icon scaleToToolbarSize(Icon icon) {
        int target = JBUI.scale(16);
        int w = icon.getIconWidth();
        if (w <= 0 || w == target) return icon;
        return IconUtil.scale(icon, null, (float) target / w);
    }

    // ── Action ────────────────────────────────────────────────────────────────

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        if (project.getBasePath() == null) return;

        if (config.isMaven()) {
            runMavenCommand(project);
        } else {
            runShellCommand(project);
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }

    // ── Maven execution ───────────────────────────────────────────────────────

    private void runMavenCommand(Project project) {
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

    // ── Shell / tool execution ────────────────────────────────────────────────

    private void runShellCommand(Project project) {
        try {
            boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
            List<String> cmd;
            if (isWindows) {
                cmd = List.of("cmd.exe", "/c", config.getGoals());
            } else {
                String shell = System.getenv("SHELL");
                if (shell == null || shell.isEmpty()) shell = "/bin/sh";
                cmd = List.of(shell, "-c", config.getGoals());
            }

            GeneralCommandLine commandLine = new GeneralCommandLine(cmd)
                    .withWorkDirectory(project.getBasePath());

            OSProcessHandler processHandler = new OSProcessHandler(commandLine);

            ConsoleView console = TextConsoleBuilderFactory.getInstance()
                    .createBuilder(project).getConsole();

            JPanel panel = new JPanel(new BorderLayout());
            panel.add(console.getComponent(), BorderLayout.CENTER);

            RunContentDescriptor descriptor = new RunContentDescriptor(
                    console, processHandler, panel, config.getLabel());

            RunContentManager.getInstance(project).showRunContent(
                    DefaultRunExecutor.getRunExecutorInstance(), descriptor);

            console.attachToProcess(processHandler);
            processHandler.startNotify();

        } catch (ExecutionException ex) {
            Messages.showErrorDialog(project, ex.getMessage(), config.getLabel());
        }
    }
}
