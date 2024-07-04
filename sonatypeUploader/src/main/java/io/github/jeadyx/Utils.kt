package io.github.jeadyx

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipOutputStream

object Utils {
    /**
     * $ curl --request POST \
     *   --verbose \
     *   --header 'Authorization: Bearer ZXhhbXBsZV91c2VybmFtZTpleGFtcGxlX3Bhca3N3b3JkCg==' \
     *   --form bundle=@central-bundle.zip \
     *   https://central.sonatype.com/api/v1/publisher/upload
     *
     */
    fun uploadFile(filePath: String, url: String, authToken: String): String {
        val file = File(filePath)
        if (file.exists()) {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $authToken")
            val boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            val outputStream = connection.outputStream
            outputStream.write("--$boundary\r\n".toByteArray())
            outputStream.write("Content-Disposition: form-data; name=\"bundle\"; filename=\"${file.name}\"\r\n".toByteArray())
            outputStream.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
            file.inputStream().use { input ->
                input.copyTo(outputStream)
            }
            outputStream.write("\r\n".toByteArray())
            outputStream.write("--$boundary--\r\n".toByteArray())
            outputStream.flush()
            outputStream.close()
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_CREATED) {
                val uid = (connection.content as InputStream).readBytes().decodeToString()
                println("[Result] Success! File Created by $uid.")
                return uid
            } else {
                throw RuntimeException("Failed to upload file. Response code: $responseCode ${connection.responseMessage}")
            }
        }else{
            throw FileNotFoundException("Upload for $filePath")

        }
    }
    /**
     * $ curl --request POST \
     *   --verbose \
     *   --header 'Authorization: Bearer ZXhhbXBsZV91c2VybmFtZTpleGFtcGxlX3Bhca3N3b3JkCg==' \
     *   'https://central.sonatype.com/api/v1/publisher/status?id=28570f16-da32-4c14-bd2e-c1acc0782365' \
     *   | jq
     *
     */
    fun checkUploadStatus(url: String, authToken: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $authToken")
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            // 将response 解析成json对象，然后根据uid获取上传状态
            val jsonObject = JsonParser.parseString(response).asJsonObject
            // PENDING, VALIDATING, VALIDATED, PUBLISHING, PUBLISHED, FAILED
            val status = jsonObject.get("deploymentState").asString
            println("[Result] Deployment status1: $status")
            if(status == "FAILED"){
                val errors = decodeUnicodeEscapes(Gson().toJson(jsonObject.get("errors")))
                throw RuntimeException("Validate FAILED\nCausing by: $errors")
            }else{
                return status
            }
        } else {
            throw RuntimeException("Failed to check upload status. Response code: $responseCode ${connection.responseMessage}")
        }
    }

    /**\
     * curl --request POST \
     *   --verbose \
     *   --header 'Authorization: Bearer ZXhhbXBsZV91c2VybmFtZTpleGFtcGxlX3Bhca3N3b3JkCg==' \
     *   'https://central.sonatype.com/api/v1/publisher/deployment/28570f16-da32-4c14-bd2e-c1acc0782365
     */
    fun publishDeployment(url: String, authToken: String){
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $authToken")
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
            println("[Result] Publish artifact invoke success.")
        } else {
            throw RuntimeException("Failed to publish deployment. Response code: $responseCode ${connection.responseMessage}")
        }
    }

    /**
     * $ curl --request DELETE \
     *   --verbose \
     *   --header 'Authorization: Bearer ZXhhbXBsZV91c2VybmFtZTpleGFtcGxlX3Bhca3N3b3JkCg==' \
     *   'https://central.sonatype.com/api/v1/publisher/deployment/28570f16-da32-4c14-bd2e-c1acc0782365
     *
     */
    fun deleteDeployment(url: String, authToken: String){
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "DELETE"
        connection.setRequestProperty("Authorization", "Bearer $authToken")
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
            println("[Result] Delete deployment success.")
        }else{
            throw RuntimeException("Failed to delete deployment. Response code: $responseCode ${connection.responseMessage}")
        }
    }

    fun zipFolder(folderPath: String, zipFilePath: String, baseZipDir: String="") {
        val zipFile = File(zipFilePath)
        println("Zip artifact")
        if (zipFile.exists()) {
            zipFile.delete()
        }
        val zipOutputStream = ZipOutputStream(java.io.FileOutputStream(zipFile))
        val file = File("${folderPath}/${baseZipDir}")
        if(!file.exists()){
            throw FileNotFoundException("The folder not found: ${file.path}")
        }
        val rootDir = let{
            if(baseZipDir.isNotBlank()) return@let baseZipDir
            if(file.isDirectory){
                return@let file.name
            }else{
                return@let ""
            }
        }
        file.listFiles()?.forEach {
            zipFolder(it.absolutePath, zipOutputStream, rootDir)
        }
        zipOutputStream.closeEntry()
        zipOutputStream.close()
    }
    private fun zipFolder(filePath: String, zipOutputStream: ZipOutputStream, baseZipDir: String) {
        val file = File(filePath)
        if (file.isDirectory) {
            val dirZip = "$baseZipDir/${file.name}"
            file.listFiles()?.forEach {
                zipFolder(it.absolutePath, zipOutputStream, dirZip)
            }
        }
        else {
            println("add file $baseZipDir/${file.name} to zip")
            zipOutputStream.putNextEntry(java.util.zip.ZipEntry("$baseZipDir/" + file.name))
            file.inputStream().use {
                it.copyTo(zipOutputStream)
            }
            zipOutputStream.closeEntry()
        }
    }
    fun writeToFile(filePath: String, content: String){
        val file = File(filePath)
        if (file.exists()) {
            file.delete()
        }
        if(!file.parentFile.exists()){
            file.parentFile.mkdirs()
        }
        file.createNewFile()
        file.writeText(content)
    }
    fun appendToFile(filePath: String, content: String){
        val file = File(filePath)
        if(!file.parentFile.exists()){
            file.parentFile.mkdirs()
        }
        file.appendText(content + "\n")
    }
    fun readFile(filePath: String): String? {
        val file = File(filePath)
        return if (file.exists()) {
            file.readText()
        } else {
            null
        }
    }
    fun getAuthToken(extension: UploaderExtension): String {
        val authName = extension.tokenName ?: run {
            throw IllegalStateException("property tokenPasswd is null")
        }
        val authPassword = extension.tokenPasswd ?: run {
            throw IllegalStateException("property tokenPasswd is null")
        }
        val authToken = encodeBase64(authName, authPassword)
        return authToken
    }
    // 将username:password进行base64编码
    private fun encodeBase64(username: String, password: String): String {
        val auth = "$username:$password"
        return java.util.Base64.getEncoder().encodeToString(auth.toByteArray())
    }
}
/**
 * {
 *   "deploymentId": "28570f16-da32-4c14-bd2e-c1acc0782365",
 *   "deploymentName": "central-bundle.zip",
 *   "deploymentState": "PUBLISHED",
 *   "purls": [
 *     "pkg:maven/com.sonatype.central.example/example_java_project@0.0.7"
 *   ]
 * }
 */
data class Deployment(
    val deploymentId: String,
    val deploymentName: String,
    val deploymentState: String,
    val purls: List<String>,
    val errors : String
)

fun decodeUnicodeEscapes(input: String): String {
    // 匹配\u后跟四个十六进制数字的Unicode转义序列
    val unicodePattern = Regex("""\\u([0-9a-fA-F]{4})""")
    return unicodePattern.replace(input) { matchResult ->
        // 将匹配的十六进制数字转换为字符
        val code = matchResult.groupValues[1].toInt(16)
        // 将Unicode码点转换为字符
        String(Character.toChars(code))
    }
}
