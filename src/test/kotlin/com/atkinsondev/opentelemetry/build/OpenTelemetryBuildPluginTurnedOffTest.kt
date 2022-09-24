package com.atkinsondev.opentelemetry.build

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import java.io.File
import java.nio.file.Path

@WireMockTest
class OpenTelemetryBuildPluginTurnedOffTest {
    @Test
    fun `when plugin is applied but no endpoint defined should log message and disable plugin`(@TempDir projectRootDirPath: Path) {
        val buildFileContents = """
            ${baseBuildFileContents()}
        """.trimIndent()

        File(projectRootDirPath.toFile(), "build.gradle").writeText(buildFileContents)

        createSrcDirectoryAndClassFile(projectRootDirPath)
        createTestDirectoryAndClassFile(projectRootDirPath)

        val buildResult = GradleRunner.create()
            .withProjectDir(projectRootDirPath.toFile())
            .withArguments("test", "--info", "--stacktrace")
            .withPluginClasspath()
            .build()

        expectThat(buildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        expectThat(buildResult.output).contains("""No OpenTelemetry build endpoint found, disabling plugin. Please add "openTelemetryBuild { endpoint = '<server>' }" to your Gradle build file.""")
    }

    @Test
    fun `when header config includes null value should disable plugin and log message`(wmRuntimeInfo: WireMockRuntimeInfo, @TempDir projectRootDirPath: Path) {
        val wiremockBaseUrl = wmRuntimeInfo.httpBaseUrl

        val buildFileContents = """
            ${baseBuildFileContents()}
            
            openTelemetryBuild {
                endpoint = '$wiremockBaseUrl/otel'
                headers = ["foo1": "bar1", "foo2": null]
                exporterMode = com.atkinsondev.opentelemetry.build.OpenTelemetryExporterMode.HTTP
            }
        """.trimIndent()

        File(projectRootDirPath.toFile(), "build.gradle").writeText(buildFileContents)

        createSrcDirectoryAndClassFile(projectRootDirPath)
        createTestDirectoryAndClassFile(projectRootDirPath)

        WireMock.stubFor(WireMock.post("/otel").willReturn(WireMock.ok()))

        val buildResult = GradleRunner.create()
            .withProjectDir(projectRootDirPath.toFile())
            .withArguments("test", "--info", "--stacktrace")
            .withPluginClasspath()
            .build()

        expectThat(buildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        expectThat(buildResult.output).contains("""Error reading config for OpenTelemetry build plugin - disabling plugin.""")

        expectThat(WireMock.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo("/otel")))).isEmpty()
    }

    @Test
    fun `when plugin is disabled should log message`(wmRuntimeInfo: WireMockRuntimeInfo, @TempDir projectRootDirPath: Path) {
        val wiremockBaseUrl = wmRuntimeInfo.httpBaseUrl

        val buildFileContents = """
            ${baseBuildFileContents()}
            
            openTelemetryBuild {
                enabled = false
                endpoint = '$wiremockBaseUrl/otel'
                exporterMode = com.atkinsondev.opentelemetry.build.OpenTelemetryExporterMode.HTTP
            }
        """.trimIndent()

        File(projectRootDirPath.toFile(), "build.gradle").writeText(buildFileContents)

        createSrcDirectoryAndClassFile(projectRootDirPath)
        createTestDirectoryAndClassFile(projectRootDirPath)

        WireMock.stubFor(WireMock.post("/otel").willReturn(WireMock.ok()))

        val buildResult = GradleRunner.create()
            .withProjectDir(projectRootDirPath.toFile())
            .withArguments("test", "--info", "--stacktrace")
            .withPluginClasspath()
            .build()

        expectThat(buildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        expectThat(buildResult.output).contains("""OpenTelemetry build plugin is disabled via configuration.""")

        expectThat(WireMock.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo("/otel")))).isEmpty()
    }
}
