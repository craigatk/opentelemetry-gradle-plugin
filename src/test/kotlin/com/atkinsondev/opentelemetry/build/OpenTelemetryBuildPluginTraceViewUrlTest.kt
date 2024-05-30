package com.atkinsondev.opentelemetry.build

import com.atkinsondev.opentelemetry.build.util.BuildOutputParser.extractTraceId
import com.github.tomakehurst.wiremock.client.WireMock.*
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
class OpenTelemetryBuildPluginTraceViewUrlTest {
    @Test
    fun `when trace view URL set should log out full trace view URL`(
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
                
                traceViewUrl = "http://localhost:16686/trace/{traceId}"
            }
            """.trimIndent()

        File(projectRootDirPath.toFile(), "build.gradle").writeText(buildFileContents)

        createSrcDirectoryAndClassFile(projectRootDirPath)
        createTestDirectoryAndClassFile(projectRootDirPath)

        stubFor(post("/otel").willReturn(ok()))

        val buildResult =
            GradleRunner.create()
                .withProjectDir(projectRootDirPath.toFile())
                .withArguments("test", "--info", "--stacktrace")
                .withPluginClasspath()
                .build()

        expectThat(buildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val traceId = extractTraceId(buildResult.output)

        expectThat(buildResult.output).contains("OpenTelemetry build trace http://localhost:16686/trace/$traceId")
    }

    @Test
    fun `when trace view type and URL set should log out full trace view URL`(
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
                
                traceViewType = com.atkinsondev.opentelemetry.build.TraceViewType.JAEGER
                traceViewUrl = "http://localhost:16686/"
            }
            """.trimIndent()

        File(projectRootDirPath.toFile(), "build.gradle").writeText(buildFileContents)

        createSrcDirectoryAndClassFile(projectRootDirPath)
        createTestDirectoryAndClassFile(projectRootDirPath)

        stubFor(post("/otel").willReturn(ok()))

        val buildResult =
            GradleRunner.create()
                .withProjectDir(projectRootDirPath.toFile())
                .withArguments("test", "--info", "--stacktrace")
                .withPluginClasspath()
                .build()

        expectThat(buildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val traceId = extractTraceId(buildResult.output)

        expectThat(buildResult.output).contains("OpenTelemetry build trace http://localhost:16686/trace/$traceId")
    }
}
