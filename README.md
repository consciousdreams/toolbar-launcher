# Toolbar Launcher

[![JetBrains Plugin](https://img.shields.io/jetbrains/plugin/v/31098-toolbar-launcher)](https://plugins.jetbrains.com/plugin/31098-toolbar-launcher)

[**Install from JetBrains Marketplace**](https://plugins.jetbrains.com/plugin/31098-toolbar-launcher)

<!-- Plugin description -->
One-click toolbar buttons to run any build command — fully customizable.

**Toolbar Launcher** adds configurable toolbar buttons to IntelliJ IDEA for running Maven, Gradle, npm,
shell, scripts, and more — no need to open tool windows or configure run configurations every time.

## Features

- **Fully configurable** — add, edit, or remove toolbar buttons from _Settings → Tools → Toolbar Launcher_
- Supports **Maven, Gradle, npm, yarn, Make, and shell commands** per button
- Set any **command** per button (e.g. `clean package -Pproduction` or `./gradlew test`)
- Assign **custom keyboard shortcuts** per button directly in the settings panel
- Choose a **built-in icon** or pick any **custom SVG** from your filesystem per button
- Buttons appear in **MainToolBar** and **NavBarToolBar** — always one click away
- Buttons are automatically **disabled** when no project is open
- Shell commands run via your **login shell** in the project root, output shown in the Run tool window
- Maven commands use the native **MavenRunner** API — output streams to the IDE's run console
- Maven properties (e.g. `-Dmaven.test.skip=true`) **never mutate** global settings — fully safe
- Enable or disable individual buttons without removing them from _Settings → Tools → Toolbar Launcher_

## Default Buttons

| Button                           | Shortcut (Mac) | Shortcut (Win/Linux) | Command                                    |
|----------------------------------|----------------|----------------------|--------------------------------------------|
| Maven Clean Install (skip tests) | `Cmd+Option+S` | `Ctrl+Alt+S`         | `mvn clean install -Dmaven.test.skip=true` |
| Maven Clean Install              | `Cmd+Option+M` | `Ctrl+Alt+M`         | `mvn clean install`                        |

## Configuration

Open **Settings → Tools → Toolbar Launcher** to manage your buttons:

- **Add** — set a label, command type, command string, icon, and optional keyboard shortcut
- **Edit** — update any field of an existing button
- **Remove** — delete a button from the toolbar
- **Enable / Disable** — toggle the checkbox in the table to hide/show a button without removing it
- **Type** — choose from Maven, Gradle, npm, yarn, Make, or Shell; pre-fills a command template and suggests a matching icon when the command field is empty
- **Custom SVG** — browse your filesystem to use any SVG file as a button icon
- **Keyboard shortcut** — click the shortcut field and press any key combination; shortcuts are registered with the IDE's Keymap system and can also be changed via **Settings → Keymap**
<!-- Plugin description end -->

## Requirements

- IntelliJ IDEA (Community or Ultimate) — builds `241` through `261.*`
- Java 17+
- The Maven plugin must be bundled with your IDE distribution (required for Maven commands)

## Build & Run

```bash
# Run plugin in a sandboxed IDE instance
./gradlew runIde

# Build distributable plugin zip (output: build/distributions/)
./gradlew buildPlugin

# Compile only
./gradlew compileJava
```

## Release & Publish

The project uses GitHub Actions for CI/CD.

### Automatic flow

1. Push to `main` → builds, verifies the plugin, and creates a **draft GitHub Release** with changelog notes
2. Review the draft on GitHub → publish it → the plugin is automatically **signed and published to JetBrains Marketplace**

### Required secrets (GitHub → Settings → Secrets)

| Secret                 | Description                                         |
|------------------------|-----------------------------------------------------|
| `PUBLISH_TOKEN`        | JetBrains Marketplace token (your profile → Tokens) |
| `CERTIFICATE_CHAIN`    | Plugin signing certificate chain                    |
| `PRIVATE_KEY`          | Plugin signing private key                          |
| `PRIVATE_KEY_PASSWORD` | Password for the private key                        |

> To generate signing keys: `./gradlew signPlugin` (first time only, follow the prompts).

## Manual Installation

1. Run `./gradlew buildPlugin`
2. Open IntelliJ IDEA → **Settings → Plugins → Install Plugin from Disk**
3. Select the `.zip` file from `build/distributions/`

## Project Structure

```
src/main/java/it/consciousdreams/
├── ActionConfig.java                   # Data model for a toolbar button
├── ToolbarLauncherSettings.java        # Persistent app-level settings service
├── ActionsRegistrar.java               # Registers dynamic actions with ActionManager on startup
├── ToolbarLauncherActionGroup.java     # Dynamic toolbar group (reads from settings)
├── ToolbarAction.java                  # AnAction that runs a configured command
├── ToolbarLauncherConfigurable.java    # Settings UI (Settings → Tools → Toolbar Launcher)
└── ActionEditDialog.java               # Add/Edit dialog for a single button

src/main/resources/
├── META-INF/plugin.xml                 # Plugin registration
├── META-INF/pluginIcon.svg             # Marketplace / Settings → Plugins logo
└── icons/                              # Built-in toolbar button SVG icons
```

## How It Works

Each toolbar button is a `ToolbarAction` instance registered with `ActionManager` under a stable UUID-based ID. On every IDE startup, `ActionsRegistrar` syncs the registered actions with the persisted settings and applies keyboard shortcuts to the active keymap.

`ToolbarLauncherActionGroup` is registered in `plugin.xml` and its `getChildren()` looks up the registered action instances from `ActionManager`, ensuring stable references (important for tooltip display).

`ToolbarAction` branches on the configured command type:
- **Maven** — parses goals and `-D` properties, delegates to `MavenRunner.getInstance(project).run()`
- **Shell/other** — runs via `$SHELL -c <command>` (or `cmd.exe /c` on Windows) using `OSProcessHandler` + `ConsoleView` in the Run tool window
