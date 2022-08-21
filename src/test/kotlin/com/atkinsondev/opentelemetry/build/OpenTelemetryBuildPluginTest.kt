package com.atkinsondev.opentelemetry.build

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import strikt.api.expectThat
import strikt.assertions.hasSize
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories

@WireMockTest
class OpenTelemetryBuildPluginTest {
    @Test
    fun `should send root span to otel`(wmRuntimeInfo: WireMockRuntimeInfo, @TempDir projectRootDirPath: Path) {
        val wiremockBaseUrl = wmRuntimeInfo.httpBaseUrl

        val buildFileContents = """
            plugins {
                id "com.atkinsondev.opentelemetry-build"
                id "org.jetbrains.kotlin.jvm" version "1.7.10"
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
            
            openTelemetryBuild {
                endpoint = '$wiremockBaseUrl/otel'
                exporterMode = com.atkinsondev.opentelemetry.build.OpenTelemetryExporterMode.HTTP
            }
        """.trimIndent()

        File(projectRootDirPath.toFile(), "build.gradle").writeText(buildFileContents)

        val srcPath = Paths.get(projectRootDirPath.absolutePathString(), "src/main/kotlin").createDirectories()
        val testPath = Paths.get(projectRootDirPath.absolutePathString(), "src/test/kotlin").createDirectories()

        val sourceFileContents = """
            fun foo() = "bar"
        """.trimIndent()
        File(srcPath.toFile(), "foo.kt").writeText(sourceFileContents)

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

        val buildResult = GradleRunner.create()
            .withProjectDir(projectRootDirPath.toFile())
            .withArguments("test", "--info", "--stacktrace")
            .withPluginClasspath()
            .build()

        val unmatchedRequests = wmRuntimeInfo.wireMock.findAllUnmatchedRequests()
        expectThat(unmatchedRequests).hasSize(1)
    }
}
