package com.atkinsondev.opentelemetry.build

import com.atkinsondev.opentelemetry.build.util.BuildOutputParser.extractTraceId
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import java.io.File
import java.nio.file.Path

class OpenTelemetryBuildPluginRemoteParentTraceConfigCacheTest : JaegerIntegrationTestCase() {
    override val healthCheckPort = 14272
    override val oltpGrpcPort = 4402
    override val queryPort = 16689

    @Test
    fun `when valid parent span and trace environment variables with config cache set should use them`(
        @TempDir projectRootDirPath: Path,
    ) {
        val buildFileContents =
            """
            ${baseBuildFileContents()}
            
            openTelemetryBuild {
              endpoint = 'http://localhost:${jaegerContainer.getMappedPort(oltpGrpcPort)}'
                supportConfigCache = true
            }
            """.trimIndent()

        File(projectRootDirPath.toFile(), "build.gradle").writeText(buildFileContents)

        createSrcDirectoryAndClassFile(projectRootDirPath)
        createTestDirectoryAndClassFile(projectRootDirPath)

        val buildResult =
            GradleRunner
                .create()
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

        val traceId = extractTraceId(buildResult.output)

        val apiResponse = fetchTrace(traceId)
        expectThat(apiResponse.data).hasSize(1)
    }
}
