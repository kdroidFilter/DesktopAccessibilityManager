plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

dependencies {
    implementation(project(":accessibility"))
}

application {
    mainClass.set("io.github.kdroidfilter.demo.swing.SwingDemoKt")
}
