package io.github.jeadyx

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.bundling.Jar
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin
import java.io.File
import java.io.FileNotFoundException
import java.time.LocalDateTime


class UploaderPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("sonatypeUploader", UploaderExtension::class.java)
        var tempRepo = project.layout.buildDirectory.dir("sonayUploader").get().asFile.path
        val hasDokkaPlugin = project.plugins.hasPlugin("org.jetbrains.dokka")
        val hasMavenPublishPlugin = project.plugins.hasPlugin(MavenPublishPlugin::class.java)
        val hasSigningPlugin = project.plugins.hasPlugin(SigningPlugin::class.java)
        val configuredManually = hasMavenPublishPlugin && hasSigningPlugin
        val hasAndroidPlugin = project.plugins.hasPlugin("android-library")
        val hasJavaPlugin = project.plugins.hasPlugin("java-library")
        if(hasDokkaPlugin){
            project.tasks.register("dokkaJavadocJar", Jar::class.java) {
                it.description = "generate javadoc, kotlin as java doc"
                it.dependsOn(project.tasks.named("dokkaJavadoc"))
                it.from(project.tasks.named("dokkaJavadoc"))
                it.archiveClassifier.set("javadoc")
            }
        }
        if(!hasMavenPublishPlugin){
            project.plugins.apply(MavenPublishPlugin::class.java)
            project.extensions.configure(PublishingExtension::class.java){
                if(hasJavaPlugin) {
                    it.publications.create("mavenJava", MavenPublication::class.java) {
                        it.from(project.components.getByName("java"))
                        if (hasDokkaPlugin) {
                            it.artifact(project.tasks.named("dokkaJavadocJar"))
                        }
                    }
                }
                it.repositories.maven {
                    it.name = "sonayUploader"
                    it.url = project.uri(tempRepo)
                }

                if(hasAndroidPlugin) {
                    it.publications.create("mavenAndroid", MavenPublication::class.java) {
                        it.artifact(
                            project.layout.buildDirectory.dir("outputs/aar/${project.name}-release.aar")
                                .get().asFile.path
                        )
                        if (hasDokkaPlugin) {
                            it.artifact(project.tasks.named("dokkaJavadocJar"))
                        }
                    }
                }
            }
        }
        if(!hasSigningPlugin){
            project.plugins.apply(SigningPlugin::class.java)
            project.extensions.configure(SigningExtension::class.java){
                it.sign(project.extensions.getByType(PublishingExtension::class.java).publications)
            }
            if(hasAndroidPlugin){
                project.tasks.named("signMavenAndroidPublication").configure{
                    it.dependsOn("bundleReleaseAar")
                }
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
            if(!configuredManually) {
                it.dependsOn("1.createDeploymentDir")
            }
            it.dependsOn("2.uploadDeploymentDir")
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
                    println("${LocalDateTime.now()} Check status: $status")
                    if(!status.endsWith("ING")) {
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
                    } else if(status == "PUBLISHING"){
                        println("[Result] The artifact is PUBLISHING, you can exec task `checkDeploymentStatus` to check publish status after few minutes.")
                        break
                    }else {
                        Thread.sleep(3000)
                    }
                }while (true)
            }
        }
        project.task("2.uploadDeploymentDir") {
            if(!configuredManually) {
                it.group = "sonatypeUploader"
                it.dependsOn("configureUploader")
            }
            it.description = "上传组合好的目录到sonatype"
            it.doLast {
                val bundleName = extension.bundleName?:"${project.name}-${project.version?:"release"}"
                val dir = File(
                    if(configuredManually){
                        extension.repositoryPath?.let{
                            extension.repositoryPath
                        }?: run {
                            throw RuntimeException("Your publication repository is not configured. Try add property `repositoryPath` to `sonatypeUploader`.")
                        }
                    }else{
                        tempRepo
                    }
                )
                if (dir.exists()) {
                    val zipFilePath = project.layout.buildDirectory.dir("sonayUploader/$bundleName.zip").get().asFile.path
                    if (!File(File(zipFilePath).parent).isDirectory) {
                        File(File(zipFilePath).parent).mkdirs()
                    } else if (File(zipFilePath).exists()) {
                        File(zipFilePath).delete()
                    }
                    Utils.zipFolder(dir.path, zipFilePath, project.group.toString().replace(".", "/"))
                    println("dir has been zipped to $zipFilePath")
                    println("upload zip file to sonatype")
                    val url = "https://central.sonatype.com/api/v1/publisher/upload"
                    val authToken = Utils.getAuthToken(extension)
                    val uid = Utils.uploadFile(zipFilePath, url, authToken)
                    System.setProperty("uploaderId", uid)
                    Utils.writeToFile("${File(zipFilePath).parent}/uploaderId", uid)
                    Utils.appendToFile(project.layout.buildDirectory.dir("tmp/sonayUploader/history").get().asFile.path, "$uid $bundleName")
                } else {
                    throw FileNotFoundException("The artifact dir not found: $dir")
                }
            }
        }
        if(!configuredManually) {
            val configureUploaderTask = project.task("configureUploader"){
                it.dependsOn("assemble")
                it.doLast {
                    extension.repositoryPath?.let{root->
                        val fileRepo = File(root)
                        if(!fileRepo.exists()){
                            if(!fileRepo.mkdirs()){
                                throw RuntimeException("[Result] Directory create Failed for $root")
                            }
                        }
                        tempRepo = root
                    }
                    project.extensions.configure(SigningExtension::class.java) {
                        extension.signing?.let { info ->
                            val signInfo = UploaderSigning("", "", "")
                            info.execute(signInfo)
                            project.extensions.extraProperties["signing.keyId"] = signInfo.keyId
                            project.extensions.extraProperties["signing.password"] =
                                signInfo.keyPasswd
                            project.extensions.extraProperties["signing.secretKeyRingFile"] =
                                signInfo.secretKeyPath
                        }
                    }
                    project.extensions.configure(PublishingExtension::class.java) {
                        extension.pom?.let { pom ->
                            if(hasJavaPlugin) {
                                it.publications.named("mavenJava", MavenPublication::class.java)
                                    .configure { publication ->
                                        pom.execute(publication.pom)
                                    }
                            }
                            if(hasAndroidPlugin){
                                it.publications.named("mavenAndroid", MavenPublication::class.java)
                                    .configure { publication ->
                                        pom.execute(publication.pom)
                                    }
                            }
                        }
                        it.repositories.named("sonayUploader", MavenArtifactRepository::class.java) {
                            it.url = project.uri(tempRepo)
                        }
                    }
                }
            }
            val cleanTask = project.tasks.register("cleanLocalDeploymentDir"){
                it.group = "sonatypeUploader"
                it.description = "清理sonatype上传目录"
                it.doLast {
                    val sonaUploaderDir = File(tempRepo)
                    if(sonaUploaderDir.exists()){
                        sonaUploaderDir.deleteRecursively()
                    }
                }
            }
            val bundleTask = project.task("1.createDeploymentDir"){ it ->
                it.group = "sonatypeUploader"
                it.description = "组合为sonatype可接受的目录树"
                it.dependsOn("configureUploader", "cleanLocalDeploymentDir")
                if(hasJavaPlugin) it.dependsOn("publishMavenJavaPublicationToSonayUploaderRepository")
                if(hasAndroidPlugin) it.dependsOn("publishMavenAndroidPublicationToSonayUploaderRepository")
                it.doLast{
                    println("Created to path: $tempRepo")
                }
            }
            bundleTask.shouldRunAfter(cleanTask, configureUploaderTask)
            oneKeyUploadTask.mustRunAfter(bundleTask)
            project.task("3.publishDeployment") {
                it.group = "sonatypeUploader"
                it.description = "发布合法的deployment"
                it.doLast {
                    val uid =
                        Utils.readFile("${project.layout.buildDirectory.get().asFile.absolutePath}/sonayUploader/uploaderId")
                    uid?.let {
                        val url = "https://central.sonatype.com/api/v1/publisher/deployment/$uid"
                        val authToken = Utils.getAuthToken(extension)
                        Utils.publishDeployment(url, authToken)
                    } ?: run {
                        throw RuntimeException("You need to upload firstly")
                    }
                }
            }
        }
        project.task("deleteDeployment") {
            it.group = "sonatypeUploader"
            it.description = "删除deployment"
            it.doLast {
                val uid =
                    Utils.readFile("${project.layout.buildDirectory.get().asFile.absolutePath}/sonayUploader/uploaderId")
                uid?.let {
                    val url = "https://central.sonatype.com/api/v1/publisher/deployment/$uid"
                    val authToken = Utils.getAuthToken(extension)
                    Utils.deleteDeployment(url, authToken)
                } ?: run {
                    throw RuntimeException("You need to upload firstly")
                }
            }
        }
        project.task("checkDeploymentStatus") {
            it.group = "sonatypeUploader"
            it.description = "获取deployment状态"
            it.doLast {
                val uid =
                    Utils.readFile("${project.layout.buildDirectory.get().asFile.absolutePath}/sonayUploader/uploaderId")
                uid?.let {
                    println("checking deployment status")
                    val url = "https://central.sonatype.com/api/v1/publisher/status?id=$uid"
                    val authToken = Utils.getAuthToken(extension)
                    val status = Utils.checkUploadStatus(url, authToken)
                    if(!listOf("PENDING", "VALIDATING", "VALIDATED", "PUBLISHING", "PUBLISHED").contains(status)){
                        throw RuntimeException("[Result] Status $status\nFor detail: https://central.sonatype.com/publishing/deployments")
                    }
                } ?: run {
                    throw RuntimeException("You need to upload firstly")
                }
            }
        }
        project.tasks.register("generateSonatypeAuthToken"){
            it.doLast {
                val token = Utils.getAuthToken(extension)
                println("Authorization: Bearer $token")
            }
        }
    }
}