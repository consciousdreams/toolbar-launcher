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

This is an IntelliJ IDEA plugin called **ToolbarLauncher** that adds fully configurable toolbar buttons to run Maven, Gradle, npm, shell commands and more.

### Data model

`ActionConfig` — a plain bean persisted via `ToolbarLauncherSettings`:
- `id` — stable UUID, used as the `ActionManager` registration key
- `label` — button tooltip text
- `goals` — full command string, e.g. `clean install -Dmaven.test.skip=true` or `./gradlew build`
- `iconPath` — `/icons/maven_install.svg` (classpath) or an absolute filesystem path to a custom SVG
- `shortcut` — `KeyStroke.toString()` format, e.g. `"meta alt pressed S"`
- `commandType` — `ToolType` id: `maven`, `gradle`, `npm`, `yarn`, `make`, `shell`

### Settings persistence

`ToolbarLauncherSettings` is an `@Service(APP)` + `PersistentStateComponent` stored in `ToolbarLauncher.xml`. It defaults to two pre-configured Maven actions (skip tests + with tests) with platform-aware shortcuts.

### Action registration

`ActionsRegistrar` implements `AppLifecycleListener` and runs `sync()` on startup. `sync()`:
1. Unregisters actions removed from settings (tracked via a static `Set<String> registeredIds` — avoids the deprecated `ActionManager.getActionIds(prefix)`)
2. Registers or refreshes each configured action in `ActionManager` under `it.consciousdreams.toolbarlauncher.{uuid}`
3. Applies/removes keyboard shortcuts on the active `Keymap`

`ToolbarLauncherConfigurable.apply()` also calls `sync()` so toolbar and shortcuts update immediately without restarting.

### Toolbar rendering

`ToolbarLauncherActionGroup` (registered in `plugin.xml` with `popup="false"`) looks up action instances from `ActionManager` in `getChildren()`. This ensures stable instances are returned on every toolbar refresh — critical for tooltip stability.

### Action execution

`ToolbarAction` branches on `config.isMaven()`:
- **Maven**: parses `config.goals` into goals list + `-D` properties map, delegates to `MavenRunner.getInstance(project).run()`
- **Shell/other**: runs via `$SHELL -c <command>` (or `cmd.exe /c` on Windows) using `OSProcessHandler` + `ConsoleView` in the Run tool window

**Important:** The `MavenRunnerParameters` constructor is overloaded. Always cast the pomFile argument explicitly as `(String) null` to avoid ambiguous call compilation errors.

### Icon loading

`ToolbarAction.loadIcon(path)` (package-accessible static):
- Paths starting with `/icons/` → `IconLoader.getIcon(path, class)` (classpath)
- All other paths → `IconLoader.findIcon(new File(path).toURI().toURL())` (filesystem)
- Always scaled to `JBUI.scale(16)` via `IconUtil.scale()`
- Falls back to `maven_install.svg` on any error

### Settings UI

`ToolbarLauncherConfigurable` (Settings → Tools → Toolbar Launcher) shows a `JBTable` with icon/type/label/command/shortcut columns and `ToolbarDecorator` for Add/Edit/Remove.

`ActionEditDialog` fields:
- **Type** — `ComboBox<ToolType>` (Maven, Gradle, npm, yarn, Make, Shell); pre-fills command with `ToolType.template` when empty
- **Label** / **Command** — text fields; label dynamically changes between "Maven Goals:" and "Command:"
- **Built-in icon** — `ComboBox` showing the two plugin icons with visual preview
- **Custom SVG** — `TextFieldWithBrowseButton` with `.svg` file filter; validated in `doValidate()`
- **Shortcut** — non-editable `JTextField` that captures key events via `KeyAdapter`; Clear button removes it

### Compatibility

- `sinceBuild` / `untilBuild` in `build.gradle.kts` must be kept in sync with the IDE version in use. Current range: `241` – `261.*`.
- The plugin depends on `org.jetbrains.idea.maven` — only works in IDE distributions that bundle the Maven plugin.
- Requires Gradle 9.0+ (`foojay-resolver-convention` must be `0.9.0`, not `1.0.0`, which is incompatible with Gradle 9.x).
