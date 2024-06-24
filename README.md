Sonatype Uploader
---
This is a gradle plugin to upload a artifact directory to maven central Repository.

# Simple to use

* `build.gradle.kts`
```kotlin
plugins {
    id("io.github.jeadyx.sonatype-uploader") version "1.0"
}

sonatypeUploader {
    dir = "your/artifact/dir"
    tokenName = tokenUsername
    tokenPasswd = tokenPassword
}
```

* `build.gradle`
```groovy
plugins {
    id 'io.github.jeadyx.sonatype-uploader' version '1.0'
}

sonatypeUploader {
  dir = "your/artifact/dir"
  tokenName = tokenUsername
  tokenPasswd = tokenPassword
}
```

* Complete Configuration
```kotlin
sonatypeUploader {
  bundleName = "$artifactName-$version" // suggest to use `$artifactName-$version`; default is "bundle-${project.name}"
  dir = "your/artifact/dir"
  tokenName = tokenUsername
  tokenPasswd = tokenPassword
}
```

After sync, you can find the gradle tasks:  
![img.png](imgs/img.png)  

# Prepare  
## Sonatype Account [Maven Central Repository](https://central.sonatype.com/)
1. Register a sonatype account.
2. Create a NameSpace.
3. Generate User Token.  
   ![img_1.png](imgs/img_1.png)

## Artifact Dir
You need to prepare a dir which can decompress to the following folder structure:  
```
`-- com
  `-- sonatype
    `-- central
      `-- example
        `-- example_java_project
          `-- 0.1.0
            |-- example_java_project-0.1.0-javadoc.jar
            |-- example_java_project-0.1.0-javadoc.jar.asc
            |-- example_java_project-0.1.0-javadoc.jar.md5
            |-- example_java_project-0.1.0-javadoc.jar.sha1
            |-- example_java_project-0.1.0-sources.jar
            |-- example_java_project-0.1.0-sources.jar.asc
            |-- example_java_project-0.1.0-sources.jar.md5
            |-- example_java_project-0.1.0-sources.jar.sha1
            |-- example_java_project-0.1.0.jar
            |-- example_java_project-0.1.0.jar.asc
            |-- example_java_project-0.1.0.jar.md5
            |-- example_java_project-0.1.0.jar.sha1
            |-- example_java_project-0.1.0.pom
            |-- example_java_project-0.1.0.pom.asc
            |-- example_java_project-0.1.0.pom.md5
            |-- example_java_project-0.1.0.pom.sha1
```
To do this:
1. You can use maven-publish plugin to generate the above folder structure.  
  reference: [maven-publish](https://docs.gradle.org/current/userguide/publishing_maven.html#ex-declaring-repositories-to-publish-to)  

2. You can use signing plugin to generate the asc file.  
  reference: [signing](https://docs.gradle.org/current/userguide/signing_plugin.html)  

## Attention  
1. keep the folder structure  
2. keep groupId is the same as the sonatype namespace  
3. before publish, you need the artifact status is `VALIDATED`  
4. bundle file name is "`bundleName`.zip"

# Reference  
This plugin is referenced from [Publishing By Using the Portal Publisher API](https://central.sonatype.org/publish/publish-portal-api/)

# Donate  

![donate.png](imgs/donate.png)
