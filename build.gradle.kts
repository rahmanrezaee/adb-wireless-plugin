import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java") // Java support
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.intelliJPlatform) // IntelliJ Platform Gradle Plugin
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.qodana) // Gradle Qodana Plugin
    alias(libs.plugins.kover) // Gradle Kover Plugin
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

// Set the JVM language level used to build the project
kotlin {
    jvmToolchain(21)
}

// Configure project's dependencies
repositories {
    mavenCentral()

    // IntelliJ Platform Gradle Plugin Repositories Extension
    intellijPlatform {
        defaultRepositories()
    }
}

// Dependencies are managed with Gradle version catalog
dependencies {

    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)

    // IntelliJ Platform Gradle Plugin Dependencies Extension
    intellijPlatform {
        // Use different IDE source based on environment
        val localIdeePath = providers.environmentVariable("LOCAL_IDE_PATH")

        if (localIdeePath.isPresent && file(localIdeePath.get()).exists()) {
            // Use local IDE if path is provided and exists (for local development)
            local(localIdeePath.get())
        } else {
            // Use downloaded IDE (for CI/CD and when local IDE not available)
            intellijIdeaCommunity(providers.gradleProperty("platformVersion"))
        }

        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map {
            it.split(',').filter { plugin -> plugin.isNotBlank() }
        })
        plugins(providers.gradleProperty("platformPlugins").map {
            it.split(',').filter { plugin -> plugin.isNotBlank() }
        })
        bundledModules(providers.gradleProperty("platformBundledModules").map {
            it.split(',').filter { module -> module.isNotBlank() }
        })

        // Testing framework
        testFramework(TestFrameworkType.Platform)
    }
}

// Configure IntelliJ Platform Gradle Plugin
intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file
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
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
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

// Configure Gradle Changelog Plugin
changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}

// Configure Gradle Kover Plugin for code coverage
kover {
    reports {
        total {
            xml {
                onCheck = true
            }
        }
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }

    publishPlugin {
        dependsOn(patchChangelog)
    }

    // Custom task to check ADB availability during build
    register("checkAdb") {
        group = "verification"
        description = "Check if ADB is available for the plugin"

        doLast {
            try {
                val adbPath = if (System.getProperty("os.name").lowercase().contains("windows")) {
                    "adb.exe"
                } else {
                    "adb"
                }

                val process = ProcessBuilder(adbPath, "version").start()
                val exitCode = process.waitFor()

                if (exitCode == 0) {
                    println("✅ ADB is available and working")
                } else {
                    println("⚠️ ADB command failed, but plugin can still be built")
                }
            } catch (e: Exception) {
                println("⚠️ ADB not found, but plugin can still be built: ${e.message}")
            }
        }
    }

    // Configure test task
    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}

// IntelliJ Platform Testing configuration
intellijPlatformTesting {
    runIde {
        register("runIdeForUiTests") {
            task {
                jvmArgumentProviders += CommandLineArgumentProvider {
                    listOf(
                        "-Drobot-server.port=8082",
                        "-Dide.mac.message.dialogs.as.sheets=false",
                        "-Djb.privacy.policy.text=<!--999.999-->",
                        "-Djb.consents.confirmation.enabled=false",
                    )
                }
            }

            plugins {
                robotServerPlugin()
            }
        }
    }
}