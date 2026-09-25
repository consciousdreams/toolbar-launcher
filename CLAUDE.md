# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Run plugin in a sandboxed IDE instance
./gradlew runIde

# Build distributable plugin zip (output: build/distributions/)
./gradlew buildPlugin

# Compile only
./gradlew compileJava
```

To install manually: **Settings → Plugins → Install Plugin from Disk**, select the `.zip` from `build/distributions/`.

## Architecture

This is an IntelliJ IDEA plugin called **Toolbar Launcher** that adds fully configurable toolbar buttons to run Maven, Gradle, npm, shell commands and more.

### Data model

`ActionConfig` — a plain bean persisted via `ToolbarLauncherSettings`. All fields are private with getters/setters (required for IntelliJ XML serialization):
- `id` — stable UUID, used in the `ActionManager` registration key `it.consciousdreams.toolbarlauncher.{uuid}`
- `label` — button tooltip text
- `goals` — full command string, e.g. `clean install -Dmaven.test.skip=true` or `./gradlew build`
- `iconPath` — `/icons/maven_install.svg` (classpath) or an absolute filesystem path to a custom SVG
- `shortcut` — `KeyStroke.toString()` format, e.g. `"meta alt pressed S"`
- `commandType` — `ToolType` id: `maven`, `gradle`, `npm`, `yarn`, `make`, `shell`, `docker`
- `enabled` — when `false`, the action is unregistered from `ActionManager` and hidden from the toolbar

### Settings persistence

`ToolbarLauncherSettings` is an `@Service(APP)` + `PersistentStateComponent` stored in `ToolbarLauncher.xml`. It defaults to two pre-configured Maven actions (skip tests + with tests) with platform-aware shortcuts.

### Action registration

`ActionsRegistrar` implements `AppLifecycleListener` and runs `sync()` on startup. `sync()`:
1. Unregisters actions removed from settings or disabled (tracked via a static `Set<String> registeredIds` — avoids the deprecated `ActionManager.getActionIds(prefix)`)
2. Registers or refreshes each **enabled** configured action in `ActionManager` under `it.consciousdreams.toolbarlauncher.{uuid}`
3. Applies/removes keyboard shortcuts on the active `Keymap`

The settings panel writes Add/Edit/Remove/Enable changes to live settings and refreshes the toolbar immediately. `ToolbarLauncherConfigurable.apply()` calls `sync()` to commit the current rows; closing Settings without applying restores the session snapshot.

On startup, `ActionsRegistrar` also subscribes to `CustomActionsListener`. When the user removes one of our buttons via the IDE's **Customize Toolbar** context menu, `handleToolbarCustomization()` detects the `DELETED` entry in `CustomActionsSchema`, marks the corresponding `ActionConfig` as disabled, removes the stale schema entry (so re-enabling from settings re-adds it correctly), and calls `sync()`. A re-entry guard (`handlingCustomization`) prevents infinite loops when modifying the schema from inside the listener.

### Toolbar rendering

`ToolbarLauncherActionGroup` (registered in `plugin.xml` with `popup="false"`) looks up action instances from `ActionManager` in `getChildren()`. Only **enabled** configs are included. This ensures stable instances are returned on every toolbar refresh — critical for tooltip stability.

### Toolbar context menu

`ConfigureToolbarButtonAction` is added to IntelliJ's `ToolbarPopupActions` group. `ToolbarButtonContextMenu`, an application service initialized by `ActionsRegistrar.sync()`, observes popup-trigger mouse events and records the clicked `ToolbarAction`'s config ID, project, and toolbar popup place. The menu item is visible only for that button and opens **Settings → Tools → Toolbar Launcher**. `ToolbarLauncherConfigurable.requestEdit(id)` selects the matching row and opens `ActionEditDialog` after the Settings panel is created.

### Action execution

`ToolbarAction` branches on `config.isMaven()`:
- **Maven**: looks up the optional `MavenCommandRunner` project service. `NativeMavenCommandRunner`, registered only by `toolbar-launcher-maven.xml`, parses `config.goals` into goals list + `-D` properties map, delegates to `MavenRunner.getInstance(project).run()`
- **Shell/other**: runs via `$SHELL -c <command>` (or `cmd.exe /c` on Windows) using `OSProcessHandler` + `ConsoleView` in the Run tool window

**Important:** The `MavenRunnerParameters` constructor is overloaded. Always cast the pomFile argument explicitly as `(String) null` to avoid ambiguous call compilation errors.

### Icon loading

`ToolbarAction.loadIcon(path)` (package-accessible static):
- Paths starting with `/icons/` → `IconLoader.getIcon(path, class)` (classpath)
- All other paths → `IconLoader.findIcon(new File(path).toURI().toURL())` (filesystem)
- Always scaled to `JBUI.scale(16)` via `IconUtil.scale()`
- Falls back to `maven_install.svg` on any error

### Settings UI

`ToolbarLauncherConfigurable` (Settings → Tools → Toolbar Launcher) shows a `JBTable` with enabled/icon/type/label/command/shortcut columns and `ToolbarDecorator` for Add/Edit/Remove/Move Up/Move Down. The **Enabled** column is a checkbox editable directly in the table without opening the edit dialog. Double-clicking a row opens the edit dialog. Move Up / Move Down reorder rows and keep the selection in sync.

`ActionEditDialog` fields:
- **Type** — `ComboBox<ToolType>` (Maven, Gradle, npm, yarn, Make, Shell, Docker); changing the type replaces the command with `ToolType.template` and selects its built-in icon
- **Label** / **Command** — text fields; label dynamically changes between "Maven Goals:" and "Command:"
- **Built-in icon** — `ComboBox` showing eight icons (including both Maven variants) with visual preview; default icon paths are defined on `ToolType.iconPath`
- **Custom SVG** — `TextFieldWithBrowseButton` with `.svg` file filter; validated in `doValidate()`
- **Shortcut** — non-editable `JTextField` that captures key events via `KeyAdapter`; Clear button removes it

### Plugin description

`README.md` is the source of truth for the Marketplace description. The section between `<!-- Plugin description -->` and `<!-- Plugin description end -->` markers is extracted by `build.gradle.kts` and converted to HTML via `markdownToHTML` (from the `gradle-changelog-plugin`). The JetBrains Marketplace only accepts a safe subset of HTML (no inline styles), so Markdown is the correct authoring format.

### Compatibility

- `pluginSinceBuild` in `gradle.properties` currently sets the minimum build to `241`; no upper build limit is declared.
- `org.jetbrains.idea.maven` is optional. Keep all Maven API references in `NativeMavenCommandRunner`, registered by the optional descriptor. The shared action code uses only `MavenCommandRunner`; if the service is absent, it displays a message suggesting a Shell action. This lets PhpStorm load the plugin without Maven.
- The Gradle wrapper is 9.4.1 and the project currently pins `foojay-resolver-convention` 0.9.0. Java 17 builds work with an installed JDK; if Gradle must provision a JDK, 0.9.0 can fail on Gradle 9 because it references the removed `JvmVendorSpec.IBM_SEMERU`. Foojay 1.0.0 fixes that incompatibility.
