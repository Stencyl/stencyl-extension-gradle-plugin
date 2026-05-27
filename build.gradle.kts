plugins {
    id("java-gradle-plugin")
    id("maven-publish")
}

group = "com.stencyl.gradle"
val baseVersion = "1.0.0"
val isReleaseVersion = project.findProperty("isReleaseVersion")?.toString()?.toBoolean() ?: false
version = if (isReleaseVersion) baseVersion else "$baseVersion-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    implementation("com.eclipsesource.minimal-json:minimal-json:0.9.5")
}

publishing {
    repositories {
        maven {
            name = "stencylMavenRepository"
            credentials(PasswordCredentials::class)
            val urlPropertyName = "stencylMavenRepository" + (if(isReleaseVersion) "ReleaseUrl" else "SnapshotUrl")
            url = uri(project.property(urlPropertyName)!!)
        }
    }
}

gradlePlugin {
    plugins {
        create("gradleStencylExtensionPlugin") {
            id = "com.stencyl.gradle.extension"
            implementationClass = "com.stencyl.gradle.extension.StencylExtensionPlugin"
        }
    }
}
