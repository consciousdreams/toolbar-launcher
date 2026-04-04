import org.jetbrains.changelog.Changelog

plugins {
    id("java")
    alias(libs.plugins.intelliJPlatform)
    alias(libs.plugins.changelog)
    alias(libs.plugins.qodana)
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

repositories {
    mavenCentral()

    // IntelliJ Platform Gradle Plugin Repositories Extension
    // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // IntelliJ Platform Gradle Plugin Dependencies Extension
    // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion"))

        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',').filter(String::isNotBlank) })
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',').filter(String::isNotBlank) })
        bundledModules(providers.gradleProperty("platformBundledModules").map { it.split(',').filter(String::isNotBlank) })
    }
}

// Configure IntelliJ Platform Gradle Plugin
// https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        // Load plugin description from dedicated HTML file
        description = providers.fileContents(layout.projectDirectory.file("pluginDescription.html")).asText

        val changelog = project.changelog // local variable for configuration cache compatibility
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // Publish to a release channel based on the version suffix (e.g. "1.0.0-beta" → "beta" channel)
        channels = providers.gradleProperty("pluginVersion").map {
            listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" })
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

// Configure Gradle Changelog Plugin -> https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
    versionPrefix = ""
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }

    publishPlugin {
        dependsOn(patchChangelog)
    }
}
