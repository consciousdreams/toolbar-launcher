# Changelog

## Unreleased

## 1.0.0

### Added
- Fully configurable toolbar buttons: add, edit, and remove buttons from **Settings → Tools → Toolbar Launcher**
- Support for **Maven, Gradle, npm, yarn, Make, and shell commands** — each button has a configurable command type
- Per-button **custom SVG icon** — choose from built-in icons or browse to any `.svg` file on disk
- Per-button **keyboard shortcut** — captured in the settings dialog and registered with the IDE Keymap
- Default buttons for `mvn clean install -Dmaven.test.skip=true` and `mvn clean install` with platform-aware shortcuts
- Plugin icon displayed in Marketplace and Settings → Plugins
- GitHub Actions CI/CD workflows for automated build, verification, signing, and Marketplace publishing