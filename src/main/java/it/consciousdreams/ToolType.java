package it.consciousdreams;

/**
 * Supported command types for toolbar actions.
 * All non-Maven types are executed via the system shell.
 */
public enum ToolType {
    MAVEN ("maven",  "Maven",  "clean install",      ActionEditDialog.DEFAULT_ICON_PATH),
    GRADLE("gradle", "Gradle", "./gradlew build",    ActionEditDialog.GRADLE_ICON_PATH),
    NPM   ("npm",    "npm",    "npm run build",      ActionEditDialog.NPM_ICON_PATH),
    YARN  ("yarn",   "yarn",   "yarn build",         ActionEditDialog.YARN_ICON_PATH),
    MAKE  ("make",   "Make",   "make all",           ActionEditDialog.MAKE_ICON_PATH),
    SHELL ("shell",  "Shell",  "",                   ActionEditDialog.SHELL_ICON_PATH),
    DOCKER("docker", "Docker", "docker compose up",  ActionEditDialog.DOCKER_ICON_PATH);

    public final String id;
    public final String displayName;
    public final String template;
    public final String iconPath;

    ToolType(String id, String displayName, String template, String iconPath) {
        this.id          = id;
        this.displayName = displayName;
        this.template    = template;
        this.iconPath    = iconPath;
    }

    public boolean isMaven() {
        return this == MAVEN;
    }

    /** Returns the ToolType matching the given id, defaulting to MAVEN for null/unknown values. */
    public static ToolType fromId(String id) {
        if (id == null) return MAVEN;
        for (ToolType t : values()) {
            if (t.id.equals(id)) return t;
        }
        com.intellij.openapi.diagnostic.Logger.getInstance(ToolType.class)
                .warn("Unknown commandType '" + id + "', defaulting to MAVEN");
        return MAVEN;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
