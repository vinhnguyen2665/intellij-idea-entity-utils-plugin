plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.4"
}

val pluginGroup: String by project
val pluginVersion: String by project
val pluginSinceBuild: String by project
val pluginUntilBuild: String? by project
val platformVersion: String by project
val platformType: String by project

group = pluginGroup
version = pluginVersion

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

intellij {
    version.set(platformVersion)
    type.set(platformType)
    plugins.set(listOf("com.intellij.java"))
}

tasks {
    patchPluginXml {
        sinceBuild.set(pluginSinceBuild)
        if (!pluginUntilBuild.isNullOrBlank()) {
            untilBuild.set(pluginUntilBuild)
        } else {
            untilBuild.set(provider { null })
        }
    }

    compileJava {
        options.encoding = "UTF-8"
    }

    buildSearchableOptions {
        enabled = false
    }
}
