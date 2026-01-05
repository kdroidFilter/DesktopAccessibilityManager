import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.publish.maven.MavenPom

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.vannitktech.maven.publish) apply false
    alias(libs.plugins.dokka) apply false
}

val ref = System.getenv("GITHUB_REF") ?: ""
val publishVersion = if (ref.startsWith("refs/tags/")) {
    val tag = ref.removePrefix("refs/tags/")
    if (tag.startsWith("v")) tag.substring(1) else tag
} else "dev"

allprojects {
    group = "io.github.kdroidfilter"
    version = publishVersion
}

fun MavenPom.configureCommonPom(pomName: String, pomDescription: String) {
    name.set(pomName)
    description.set(pomDescription)
    inceptionYear.set("2026")
    url.set("https://github.com/kdroidFilter/DesktopAccessibilityManager")

    licenses {
        license {
            name.set("MIT")
            url.set("https://opensource.org/licenses/MIT")
        }
    }

    developers {
        developer {
            id.set("kdroidfilter")
            name.set("Elyahou Gambache")
            email.set("elyahou.hadass@gmail.com")
        }
    }

    scm {
        url.set("https://github.com/kdroidFilter/DesktopAccessibilityManager")
    }
}

subprojects {
    plugins.withId("com.vanniktech.maven.publish") {
        extensions.configure<MavenPublishBaseExtension>("mavenPublishing") {
            val pomName = project.findProperty("pomName") as String? ?: project.name
            val pomDescription = project.findProperty("pomDescription") as String?
                ?: project.description
                ?: project.name
            val skipSigning = providers.gradleProperty("skipSigning").orNull == "true"
            val signingKey = providers.gradleProperty("signingInMemoryKey").orNull
            val signingKeyId = providers.gradleProperty("signingInMemoryKeyId").orNull
            val signingKeyPassword = providers.gradleProperty("signingInMemoryKeyPassword").orNull

            pom {
                configureCommonPom(pomName, pomDescription)
            }

            publishToMavenCentral()
            if (!skipSigning && !signingKey.isNullOrBlank() && !signingKeyId.isNullOrBlank() && !signingKeyPassword.isNullOrBlank()) {
                signAllPublications()
            }
        }
    }
}
