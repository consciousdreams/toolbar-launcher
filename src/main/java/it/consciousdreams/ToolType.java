package it.consciousdreams;

/**
 * Supported command types for toolbar actions.
 * All non-Maven types are executed via the system shell.
 */
public enum ToolType {
    MAVEN ("maven",  "Maven",  "clean install"),
    GRADLE("gradle", "Gradle", "./gradlew build"),
    NPM   ("npm",    "npm",    "npm run build"),
    YARN  ("yarn",   "yarn",   "yarn build"),
    MAKE  ("make",   "Make",   "make all"),
    SHELL ("shell",  "Shell",  "");

    public final String id;           // stored in MavenActionConfig.commandType
    public final String displayName;
    public final String template;     // pre-filled in the dialog command field

    ToolType(String id, String displayName, String template) {
        this.id          = id;
        this.displayName = displayName;
        this.template    = template;
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
        return MAVEN;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
