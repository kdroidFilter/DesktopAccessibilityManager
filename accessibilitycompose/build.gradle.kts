plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.vannitktech.maven.publish)
    alias(libs.plugins.dokka)
}

extra["pomName"] = "Desktop Accessibility Compose"
extra["pomDescription"] = "Compose Desktop helpers for DesktopAccessibilityManager (CompositionLocal support)."

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(compose.runtime)
            implementation(project(":accessibility"))
        }
    }
}

mavenPublishing {
    coordinates(
        groupId = project.group.toString(),
        artifactId = "accessibility-compose",
        version = project.version.toString()
    )
}
