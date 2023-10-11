package com.atkinsondev.opentelemetry.build

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories

fun createSrcDirectoryAndClassFile(projectRootDirPath: Path) {
    val srcPath = Paths.get(projectRootDirPath.absolutePathString(), "src/main/kotlin").createDirectories()

    val sourceFileContents = """
            fun foo() = "bar"
    """.trimIndent()

    File(srcPath.toFile(), "foo.kt").writeText(sourceFileContents)
}

fun createTestDirectoryAndClassFile(projectRootDirPath: Path) {
    val testPath = Paths.get(projectRootDirPath.absolutePathString(), "src/test/kotlin").createDirectories()

    val testFileContents = """
            import org.junit.jupiter.api.Test
            
            class FooTest {
                @Test
                fun `foo should return bar`() {
                    assert(foo() == "bar")
                }
            }
    """.trimIndent()

    File(testPath.toFile(), "FooTest.kt").writeText(testFileContents)
}

fun createTestDirectoryAndFailingClassFile(projectRootDirPath: Path) {
    val testPath = Paths.get(projectRootDirPath.absolutePathString(), "src/test/kotlin").createDirectories()

    val testFileContents = """
            import org.junit.jupiter.api.Test
            
            class FooTest {
                @Test
                fun `foo should return bar but will fail`() {
                    assert(foo() == "baz")
                }
            }
    """.trimIndent()

    File(testPath.toFile(), "FooTest.kt").writeText(testFileContents)
}

fun baseBuildFileContents(): String = """
    buildscript {
        repositories {
            gradlePluginPortal()
            mavenCentral()
        }
    }
    
    plugins {
        id "com.atkinsondev.opentelemetry-build"
        id "org.jetbrains.kotlin.jvm" version "1.6.21"
    }
    
    repositories {
        mavenCentral()
    }
    
    dependencies {
        testImplementation(platform("org.junit:junit-bom:5.9.0"))
        testImplementation("org.junit.jupiter:junit-jupiter")
    }
    
    test {
        useJUnitPlatform()
    }
""".trimIndent()

fun baseKotlinBuildFileContents(): String = """
    buildscript {
        repositories {
            mavenCentral()
        }
    }

    plugins {
        id("org.jetbrains.kotlin.jvm") version "1.9.10"
        id("com.atkinsondev.opentelemetry-build")
    }
    
    repositories {
        mavenCentral()
    }
    
    dependencies {
        testImplementation(platform("org.junit:junit-bom:5.9.0"))
        testImplementation("org.junit.jupiter:junit-jupiter")
    }
    
    tasks.named<Test>("test") {
        useJUnitPlatform()
    }
""".trimIndent()
