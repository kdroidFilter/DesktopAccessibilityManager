plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation("net.java.dev.jna:jna:5.18.1")
            implementation("net.java.dev.jna:jna-platform:5.18.1")
        }
    }
}
