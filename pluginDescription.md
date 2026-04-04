One-click toolbar buttons to run any build command — fully customizable.

**ToolbarLauncher** adds configurable toolbar buttons to IntelliJ IDEA for running Maven, Gradle, npm,
shell scripts and more — no need to open tool windows or configure run configurations every time.

## Default Toolbar Buttons

| Button | Mac | Win / Linux | Command |
|--------|-----|-------------|---------|
| **!m** – Skip Tests | ⌘+⌥+S | Ctrl+Alt+S | `mvn clean install -Dmaven.test.skip=true` |
| **m** – With Tests | ⌘+⌥+M | Ctrl+Alt+M | `mvn clean install` |

## Features

- **Fully configurable** — add, edit, or remove toolbar buttons from _Settings → Tools → Toolbar Launcher_
- Supports **Maven, Gradle, npm, yarn, Make, and shell commands** per button
- Set any **command** per button (e.g. `clean package -Pproduction` or `./gradlew test`)
- Assign **custom keyboard shortcuts** per button directly in the settings panel
- Choose a **built-in icon** or pick any **custom SVG** from your filesystem per button
- Buttons appear in **MainToolBar** and **NavBarToolBar** — always one click away
- Buttons are automatically **disabled** when no project is open
- Maven commands use the native **MavenRunner** API — output streams to the IDE's run console
- Shell commands run via your **login shell** in the project root, output shown in the Run tool window
- Maven properties (e.g. `-Dmaven.test.skip=true`) **never mutate** global settings — fully safe
- Enable or disable individual buttons without removing them from _Settings → Tools → Toolbar Launcher_

## Requirements

- **IntelliJ IDEA** Community or Ultimate (2024.1 or later)
- Maven commands require the project to use **Maven** as its build system

---

Open source — [GitHub Repository](https://github.com/consciousdreams/toolbar-launcher)
