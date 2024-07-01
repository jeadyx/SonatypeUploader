package io.github.jeadyx

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.bundling.Jar
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin
import java.io.File
import java.io.FileNotFoundException
import java.lang.RuntimeException


class UploaderPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("sonatypeUploader", UploaderExtension::class.java)
        val tempRepo = project.layout.buildDirectory.dir("sonayUploader")
        val hasDokkaPlugin = project.plugins.hasPlugin("org.jetbrains.dokka")
        if(hasDokkaPlugin){
            project.tasks.register("dokkaJavadocJar", Jar::class.java) {
                it.description = "generate javadoc, kotlin as java doc"
                it.dependsOn(project.tasks.named("dokkaJavadoc"))
                it.from(project.tasks.named("dokkaJavadoc"))
                it.archiveClassifier.set("javadoc")
            }
        }
        if(!project.plugins.hasPlugin(MavenPublishPlugin::class.java)){
            project.plugins.apply(MavenPublishPlugin::class.java)
            project.extensions.configure(PublishingExtension::class.java){
                it.publications.create("mavenJava", MavenPublication::class.java){
                    it.from(project.components.getByName("java"))
                    if(hasDokkaPlugin) {
                        it.artifact(project.tasks.named("dokkaJavadocJar"))
                    }
                }
                it.repositories.maven {
                    it.name = "sonayUploader"
                    it.url = project.uri(tempRepo)
                }
            }
        }
        if(!project.plugins.hasPlugin(SigningPlugin::class.java)){
            project.plugins.apply(SigningPlugin::class.java)
            project.extensions.configure(SigningExtension::class.java){
                it.sign(project.extensions.getByType(PublishingExtension::class.java).publications)
            }
        }
        if(project.plugins.hasPlugin("java-library")){
            project.extensions.configure(JavaPluginExtension::class.java){
                if(!hasDokkaPlugin) {
                    it.withJavadocJar()
                }
                it.withSourcesJar()
            }
        }
        val oneKeyUploadTask = project.task("publishToSonatype"){
            it.group = "sonatypeUploader"
            it.description = "一键发布到sonatype"
            it.dependsOn("0.test bundle dir")
            it.dependsOn ("1.upload deployment dir")
            it.doLast {
                println("Maven has upload completed.")
                println("Checking maven validate status.")
                val authToken = Utils.getAuthToken(extension)
                val uid = Utils.readFile("${project.layout.buildDirectory.get().asFile.absolutePath}/sonayUploader/uploaderId")
                do {
                    val status = Utils.checkUploadStatus(
                        "https://central.sonatype.com/api/v1/publisher/status?id=$uid",
                        authToken
                    )
                    println("Check status: $status")
                    if((status != "PENDING") && (status != "PUBLISHING")) {
                        if (status == "VALIDATED") {
                            println("Artifact is validated")
                            println("Invoke publish api")
                            Utils.publishDeployment(
                                "https://central.sonatype.com/api/v1/publisher/deployment/$uid",
                                authToken
                            )
                        }else if(status == "PUBLISHED"){
                            println("[Result] Congratulations! Your artifact has been published.")
                            break
                        }else{
                            throw RuntimeException("[Result] Validate fail causing by $status\nFor detail: https://central.sonatype.com/publishing/deployments")
                        }
                    }
                    Thread.sleep(3000)
                }while (true)

            }
        }
        val configureMavenTask = project.task("configureMavenPom"){
            it.doLast {
                extension.pom?.let {pom->
                    project.extensions.configure(PublishingExtension::class.java) {
                        it.publications.named("mavenJava", MavenPublication::class.java).configure {publication->
                            pom.execute(publication.pom)
                        }
                    }
                }
            }
        }
        val bundleTask = project.task("0.test bundle dir"){ it ->
            it.group = "sonatypeUploader"
            it.description = "组合为sonatype可接受的目录树"
            it.dependsOn("configureMavenPom")
            it.dependsOn("assemble", "publishMavenJavaPublicationToSonayUploaderRepository")
            it.doLast{
                println("Done bundle to path: ${tempRepo.get().asFile.path}")
            }
        }
        bundleTask.shouldRunAfter(configureMavenTask)
        oneKeyUploadTask.mustRunAfter(bundleTask)
        project.task("1.upload deployment dir") {
            it.group = "sonatypeUploader"
            it.description = "上传组合好的目录到sonatype"
            it.doLast {
                val packageRootDir = project.group.toString().split(".")[0]
                val bundleName = extension.bundleName?:"${project.name}-${project.version?:"release"}"
                val dir = File(extension.artifactRoot?:"${tempRepo.get().asFile.path}/$packageRootDir")
                if (dir.exists()) {
                    println("Zip artifact files to $dir")
                    val zipFilePath =
                        "${tempRepo.get().asFile.path}/$bundleName.zip"
                    if (!File(File(zipFilePath).parent).isDirectory) {
                        File(File(zipFilePath).parent).mkdirs()
                    } else if (File(zipFilePath).exists()) {
                        File(zipFilePath).delete()
                    }
                    Utils.zipFolder(dir.absolutePath, zipFilePath)
                    println("dir has been zipped to $zipFilePath")
                    println("upload zip file to sonatype")
                    val url = "https://central.sonatype.com/api/v1/publisher/upload"
                    val authToken = Utils.getAuthToken(extension)
                    val uid = Utils.uploadFile(zipFilePath, url, authToken)
                    System.setProperty("uploaderId", uid)
                    Utils.writeToFile("${File(zipFilePath).parent}/uploaderId", uid)
                } else {
                    throw FileNotFoundException("$dir")
                }
            }
        }
        project.task("2.check deployment status") {
            it.group = "sonatypeUploader"
            it.description = "获取deployment状态"
            it.doLast {
                val uid =
                    Utils.readFile("${project.layout.buildDirectory.get().asFile.absolutePath}/sonayUploader/uploaderId")
                uid?.let {
                    println("checking deployment status")
                    val url = "https://central.sonatype.com/api/v1/publisher/status?id=$uid"
                    val authToken = Utils.getAuthToken(extension)
                    Utils.checkUploadStatus(url, authToken)
                }?:run{
                    println("You need to upload firstly")
                }
            }
        }
        project.task("3.publish deployment") {
            it.group = "sonatypeUploader"
            it.description = "发布合法的deployment"
            it.doLast {
                val uid =
                    Utils.readFile("${project.layout.buildDirectory.get().asFile.absolutePath}/sonayUploader/uploaderId")
                uid?.let{
                    val url = "https://central.sonatype.com/api/v1/publisher/deployment/$uid"
                    val authToken = Utils.getAuthToken(extension)
                    Utils.publishDeployment(url, authToken)
                }?:run{
                    println("You need to upload firstly")
                }
            }
        }
        project.task("3.delete deployment") {
            it.group = "sonatypeUploader"
            it.description = "删除deployment"
            it.doLast {
                val uid =
                    Utils.readFile("${project.layout.buildDirectory.get().asFile.absolutePath}/sonayUploader/uploaderId")
                uid?.let {
                    val url = "https://central.sonatype.com/api/v1/publisher/deployment/$uid"
                    val authToken = Utils.getAuthToken(extension)
                    Utils.deleteDeployment(url, authToken)
                }?:run{
                    println("You need to upload firstly")
                }
            }
        }
    }
}