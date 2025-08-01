package com.atkinsondev.opentelemetry.build

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories

fun createSrcDirectoryAndClassFile(projectRootDirPath: Path) {
    val srcPath = Paths.get(projectRootDirPath.absolutePathString(), "src/main/kotlin").createDirectories()

    val sourceFileContents =
        """
        fun foo() = "bar"
        
        fun bar() = "foo"
        """.trimIndent()

    File(srcPath.toFile(), "foo.kt").writeText(sourceFileContents)
}

fun createTestDirectoryAndClassFile(projectRootDirPath: Path) {
    val testPath = Paths.get(projectRootDirPath.absolutePathString(), "src/test/kotlin").createDirectories()

    val fooTestFileContents =
        """
        import org.junit.jupiter.api.Test
        import org.junit.jupiter.api.TestMethodOrder
        import org.junit.jupiter.api.MethodOrderer
        
        @TestMethodOrder(MethodOrderer.MethodName::class)
        class FooTest {
            @Test
            fun `foo should return bar`() {
                assert(foo() == "bar")
            }
            
            @Test
            fun `foo should not return baz`() {
                assert(foo() != "baz")
            }
        }
        """.trimIndent()
    File(testPath.toFile(), "FooTest.kt").writeText(fooTestFileContents)

    val barTestFileContents =
        """
        import org.junit.jupiter.api.Test
        
        import org.junit.jupiter.api.TestMethodOrder
        import org.junit.jupiter.api.MethodOrderer
        
        @TestMethodOrder(MethodOrderer.MethodName::class)
        class BarTest {
            @Test
            fun `bar should return foo`() {
                assert(bar() == "foo")
            }
            
            @Test
            fun `bar should not return baz`() {
                assert(bar() != "baz")
            }
        }
        """.trimIndent()
    File(testPath.toFile(), "BarTest.kt").writeText(barTestFileContents)
}

fun createTestDirectoryAndFailingClassFile(projectRootDirPath: Path) {
    val testPath = Paths.get(projectRootDirPath.absolutePathString(), "src/test/kotlin").createDirectories()

    val testFileContents =
        """
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

fun baseBuildFileContents(): String =
    """
    buildscript {
        repositories {
            gradlePluginPortal()
            mavenCentral()
        }
    }
    
    plugins {
        id "com.atkinsondev.opentelemetry-build"
        id "org.jetbrains.kotlin.jvm" version "2.2.0"
    }
    
    repositories {
        mavenCentral()
    }
    
    dependencies {
        testImplementation(platform("org.junit:junit-bom:5.9.0"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }
    
    test {
        useJUnitPlatform()
    }
    """.trimIndent()

fun baseKotlinBuildFileContents(): String =
    """
    buildscript {
        repositories {
            mavenCentral()
        }
    }

    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.2.0"
        id("com.atkinsondev.opentelemetry-build")
    }
    
    repositories {
        mavenCentral()
    }
    
    dependencies {
        testImplementation(platform("org.junit:junit-bom:5.9.0"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }
    
    tasks.named<Test>("test") {
        useJUnitPlatform()
    }
    """.trimIndent()
