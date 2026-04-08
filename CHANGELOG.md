# Changelog

## [Unreleased]

### Added

- Double-click on a row in the settings table opens the edit dialog
- Move Up / Move Down buttons in the settings table to reorder toolbar buttons
- Removing a button via the IDE's **Customize Toolbar** context menu now automatically disables it in Toolbar Launcher settings; the schema entry is also cleaned up so re-enabling it from settings works correctly

### Fixed

- Action template presentation text was missing, causing a WARN in the Keymap settings panel

### Changed

- Keyboard shortcut changes made via **Settings → Keymap** are now reflected immediately in the Toolbar Launcher settings panel
- Toolbar buttons update asynchronously after dynamic plugin actions are registered on startup
- GitHub Actions bumped to latest versions (checkout v6, setup-java v5, setup-gradle v5, upload-artifact v6)

## [1.0.1]

### Added

- Docker command type support (`docker compose up` default template)
- Tooltip on the Enable checkbox showing "Click to enable / disable this button"

### Changed

- Selecting a different command type now always updates the command and icon
- Plugin Marketplace description is now sourced from README.md

## [1.0.0]

### Added

- Fully configurable toolbar buttons: add, edit, and remove buttons from **Settings → Tools → Toolbar Launcher**
- Support for **Maven, Gradle, npm, yarn, Make, and shell commands** — each button has a configurable command type
- Per-button **custom SVG icon** — choose from built-in icons or browse to any `.svg` file on disk
- Per-button **keyboard shortcut** — captured in the settings dialog and registered with the IDE Keymap
- Default buttons for `mvn clean install -Dmaven.test.skip=true` and `mvn clean install` with platform-aware shortcuts
- Plugin icon displayed in Marketplace and Settings → Plugins
- GitHub Actions CI/CD workflows for automated build, verification, signing, and Marketplace publishing

[Unreleased]: https://github.com/consciousdreams/toolbar-launcher/compare/1.0.1...HEAD
[1.0.1]: https://github.com/consciousdreams/toolbar-launcher/compare/1.0.0...1.0.1
[1.0.0]: https://github.com/consciousdreams/toolbar-launcher/commits/1.0.0
