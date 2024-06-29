plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    signing
    alias(libs.plugins.gradle.plugin.publish)
    id("org.jetbrains.dokka") version "1.9.20" apply false
}
group = "io.github.jeadyx"
version = "2.0"
gradlePlugin {
    website.set("https://github.com/jeadyx/SonatypeUploader")
    vcsUrl.set("https://github.com/jeadyx/SonatypeUploader")
    plugins {
        create("sonatype uploader") {
            id = "io.github.jeadyx.sonatype-uploader"
            implementationClass = "io.github.jeadyx.UploaderPlugin"
            displayName = "Sonatype Uploader"
            description = "Upload your maven artifact to Sonatype repo"
            tags.set(listOf("sonatype", "uploader", "maven"))
        }
    }
}
dependencies{
    implementation("com.google.code.gson:gson:2.8.9")
}