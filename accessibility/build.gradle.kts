plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.vannitktech.maven.publish)
    alias(libs.plugins.dokka)
}

extra["pomName"] = "Desktop Accessibility Announcer"
extra["pomDescription"] = "JVM-only helper that provides desktop accessibility announcements using JNA."

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation("net.java.dev.jna:jna:5.18.1")
            implementation("net.java.dev.jna:jna-platform:5.18.1")
        }
    }
}

mavenPublishing {
    coordinates(
        groupId = project.group.toString(),
        artifactId = "accessibility",
        version = project.version.toString()
    )
}
