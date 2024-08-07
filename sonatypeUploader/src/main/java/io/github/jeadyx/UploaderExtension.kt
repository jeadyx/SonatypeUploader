package io.github.jeadyx

import org.gradle.api.Action
import org.gradle.api.publish.maven.MavenPom

interface UploaderExtension {
    /**
     * 打包压缩包的名称，也是上传到sonatype后显示的名称
     */
    var bundleName: String?

    /**
     * 如果只使用上传功能，该参数用于指定要上传工件的本地仓库地址，如E:\\repo
     */
    var repositoryPath: String?

    /**
     * sonatype 上生成的token username
     */
    var tokenName: String?

    /**
     * sonatype 上生成的token password
     */
    var tokenPasswd: String?

    /**
     * 要生成的pom文件信息
     */
    var pom: Action<MavenPom>?

    /**
     * 用于签名的信息
     */
    var signing: Action<UploaderSigning>?
}

data class UploaderSigning(
    var keyId: String,
    var keyPasswd: String,
    var secretKeyPath: String
)