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
import strikt.assertions.isEqualTo
import java.io.File
import java.nio.file.Path

@WireMockTest
class OpenTelemetryBuildPluginRemoteParentTraceTest {
    @Test
    fun `when valid parent span and trace environment variables set should use them`(
        wmRuntimeInfo: WireMockRuntimeInfo,
        @TempDir projectRootDirPath: Path,
    ) {
        val wiremockBaseUrl = wmRuntimeInfo.httpBaseUrl

        val buildFileContents =
            """
            ${baseBuildFileContents()}
            
            openTelemetryBuild {
                endpoint = '$wiremockBaseUrl/otel'
                exporterMode = com.atkinsondev.opentelemetry.build.OpenTelemetryExporterMode.HTTP
            }
            """.trimIndent()

        File(projectRootDirPath.toFile(), "build.gradle").writeText(buildFileContents)

        createSrcDirectoryAndClassFile(projectRootDirPath)
        createTestDirectoryAndClassFile(projectRootDirPath)

        WireMock.stubFor(WireMock.post("/otel").willReturn(WireMock.ok()))

        val buildResult =
            GradleRunner.create()
                .withProjectDir(projectRootDirPath.toFile())
                .withArguments("test", "--info", "--stacktrace")
                .withEnvironment(mapOf("SPAN_ID" to "f1a2153e247b0d94", "TRACE_ID" to "a263fdf001993a32980b9ec5740b7d6d"))
                .withPluginClasspath()
                .build()

        println(buildResult.output)

        expectThat(buildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        expectThat(buildResult.output).not().contains("Remote span context is not valid")

        expectThat(
            buildResult.output,
        ).contains("Using parent span ID f1a2153e247b0d94 and parent trace ID a263fdf001993a32980b9ec5740b7d6d")
        expectThat(buildResult.output).contains("OpenTelemetry build trace ID a263fdf001993a32980b9ec5740b7d6d")
    }
}
