import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip

plugins {
    java
}

group = "dev.weissenba.aerovista"
version = "1.1.0"

val pluginName = "AeroVista"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    compileOnly(fileTree("/Applications/IntelliJ IDEA.app/Contents/lib") {
        include("*.jar")
    })
}

val validateTheme by tasks.registering(Exec::class) {
    group = "verification"
    description = "Validates the theme JSON, XML files, references, and core contrast pairs."
    commandLine("python3", "scripts/validate_theme.py")
}

val pluginJar by tasks.registering(Jar::class) {
    dependsOn(validateTheme, tasks.named("classes"))
    group = "build"
    description = "Packages the resource-only IntelliJ theme plugin JAR."
    archiveBaseName.set(pluginName)
    archiveVersion.set(project.version.toString())
    from(sourceSets.main.get().output)
}

val buildPlugin by tasks.registering(Zip::class) {
    dependsOn(pluginJar)
    group = "build"
    description = "Builds the installable IntelliJ plugin ZIP."
    archiveBaseName.set(pluginName)
    archiveVersion.set(project.version.toString())
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    into("$pluginName/lib") {
        from(pluginJar.flatMap { it.archiveFile })
    }
}

tasks.named("check") {
    dependsOn(validateTheme)
}

tasks.named("build") {
    dependsOn(buildPlugin)
}
