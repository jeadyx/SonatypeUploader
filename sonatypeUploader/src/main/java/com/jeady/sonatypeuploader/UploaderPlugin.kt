package com.jeady.sonatypeuploader

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File
import java.io.FileNotFoundException

/* Usage for uploader to sonatype repo
    apply plugin: 'com.jeady.sonatypeuploader'
    sonatypeUploader {
        bundleName = "bundleName"
        dir = "dir"
        tokenName = "tokenName"
        tokenPasswd = "tokenPasswd"
    }
*/
class UploaderPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("sonatypeUploader", UploaderExtension::class.java)
        project.task("1.upload deployment dir") {
            it.group = "sonatypeUploader"
            it.description = "上传约定目录到sonatype"
            it.doLast {
                val bundleName = extension.bundleName.getOrElse("bundle-${project.name}")
                val dir = File(extension.dir.get())
                if (dir.exists()) {
                    println("prepare to upload dir $dir")
                    val zipFilePath =
                        "${project.layout.buildDirectory.get().asFile.absolutePath}/sonayUploader/$bundleName.zip"
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
        project.task("3.publish deployment") {
            it.group = "sonatypeUploader"
            it.description = "发布合法的deployment"
            it.doLast {
                val uid = Utils.readFile("${project.layout.buildDirectory.get().asFile.absolutePath}/sonayUploader/uploaderId")
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
                val uid = Utils.readFile("${project.layout.buildDirectory.get().asFile.absolutePath}/sonayUploader/uploaderId")
                uid?.let {
                    val url = "https://central.sonatype.com/api/v1/publisher/deployment/$uid"
                    val authToken = Utils.getAuthToken(extension)
                    Utils.deleteDeployment(url, authToken)
                }?:run{
                    println("You need to upload firstly")
                }
            }
        }

        project.task("2.check deployment status") {
            it.group = "sonatypeUploader"
            it.description = "获取deployment状态"
            it.doLast {
                val uid = Utils.readFile("${project.layout.buildDirectory.get().asFile.absolutePath}/sonayUploader/uploaderId")
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
    }
}