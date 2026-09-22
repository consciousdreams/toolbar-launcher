package it.consciousdreams;

import com.intellij.openapi.project.Project;

/** Optional project service; the shared action code does not reference Maven APIs. */
public interface MavenCommandRunner {
    void run(Project project, ActionConfig config);
}
