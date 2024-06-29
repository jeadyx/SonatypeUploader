Sonatype Uploader
---
This is a gradle plugin to upload a artifact directory to maven central Repository.

# Simple to use

* `build.gradle.kts`
```kotlin
plugins { 
    id("io.github.jeadyx.sonatype-uploader") version "2.0"
    id("org.jetbrains.dokka") version "1.9.20"
}

group = "io.github.jeady5"
version = "1.0"
sonatypeUploader {
    tokenName = "tokenUser"
    tokenPasswd = "tokenUserPasswd"
    pom = Action<MavenPom>{
        name = "My Library"
        description = "A concise description of my library greennbg"
        url = "http://www.example.com/library"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "jeady"
                name = "jeady"
                email = "jeady@example.com"
            }
        }
        scm {
            connection = "scm:git:git://example.com/my-library.git"
            developerConnection = "scm:git:ssh://example.com/my-library.git"
            url = "http://example.com/my-library/"
        }
    }
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

## Signing key   
reference: [signing](https://docs.gradle.org/current/userguide/signing_plugin.html)  

# Sample
[SonatypeUploaderSample Project](https://github.com/jeadyx/SonatypeUploaderSample)

# Reference  
This plugin is referenced from [Publishing By Using the Portal Publisher API](https://central.sonatype.org/publish/publish-portal-api/)

# Donate  

![donate.png](imgs/donate.png)
