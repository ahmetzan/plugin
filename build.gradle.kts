import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.migros"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // IntelliJ Community Edition 2025.1 kullan
        create("IC", "2025.1")

        // Test framework
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Java PSI API için gerekli plugin
        bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {

    pluginVerification{
        ides{
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2025.1")
            select {
                types = listOf(IntelliJPlatformType.IntellijIdeaCommunity)
                sinceBuild = "251.1"
                untilBuild = "251.1"
            }
        }
    }

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
        }
        changeNotes = """
            Initial version with basic functionality.
        """.trimIndent()
    }

}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }

    publishPlugin{
        token.set("perm-QWhtZXRfQ2FuX1phbg==.OTItMTMyNjg=.dneEP4XAH3LTJHZqwvtYK2hDEbqYGC")
    }
}