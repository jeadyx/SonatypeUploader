package com.jeady.sonatypeuploader

import org.gradle.api.provider.Property

interface UploaderExtension {
    val bundleName: Property<String?>
    val dir: Property<String>
    val tokenName: Property<String>
    val tokenPasswd: Property<String>
}