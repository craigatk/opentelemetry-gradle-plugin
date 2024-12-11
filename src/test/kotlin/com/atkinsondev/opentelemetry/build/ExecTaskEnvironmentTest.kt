package com.atkinsondev.opentelemetry.build

import com.atkinsondev.opentelemetry.build.util.BuildOutputParser.extractTraceId
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertLinesMatch
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import java.io.File
import java.nio.file.Path

class ExecTaskEnvironmentTest : JaegerIntegrationTestCase() {
    override val healthCheckPort = 14273
    override val oltpGrpcPort = 4403
    override val queryPort = 16671

    @ParameterizedTest
    @MethodSource("com.atkinsondev.opentelemetry.build.util.GradleTestVersions#versions")
    fun `should put environment variables with trace and span IDs without config cache param enabled`(
        gradleVersion: String,
        @TempDir projectRootDirPath: Path,
    ) {
        val buildFileContents =
            """
            ${baseBuildFileContents()}

            openTelemetryBuild {
                endpoint = 'http://localhost:${jaegerContainer.getMappedPort(oltpGrpcPort)}'
                taskTraceEnvironmentEnabled = true
                
                traceViewUrl = "http://localhost:16686/trace/{traceId}"
            }
            
            task printEnv(type: Exec) {
                commandLine "/bin/bash", "script.sh"
            }
            """.trimIndent()
        File(projectRootDirPath.toFile(), "build.gradle").writeText(buildFileContents)

        val scriptFileContents = "echo traceid: \$TRACE_ID spanid: \$SPAN_ID".trimIndent()
        File(projectRootDirPath.toFile(), "script.sh").writeText(scriptFileContents)

        val buildResult =
            GradleRunner.create()
                .withProjectDir(projectRootDirPath.toFile())
                .withArguments("printEnv", "--info", "--stacktrace")
                .withGradleVersion(gradleVersion)
                .withPluginClasspath()
                .build()

        expectThat(buildResult.task(":printEnv")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val traceId = extractTraceId(buildResult.output)
        val trace = fetchTrace(traceId = traceId, verifyRootSpanId = true)
        expectThat(trace.data).hasSize(1)

        val rootSpanId = trace.data.first().spans.find { it.isRoot() }?.spanID
        expectThat(rootSpanId).isNotNull()

        expectThat(buildResult.output).contains("traceid: $traceId spanid: $rootSpanId")

        val orderedSpansNamesWithDepth = fetchSpanNamesWithDepth(traceId)
        assertLinesMatch(
            listOf(
                " junit-\\d+-build",
                "> :printEnv",
            ),
            orderedSpansNamesWithDepth,
        )
    }

    @ParameterizedTest
    @MethodSource("com.atkinsondev.opentelemetry.build.util.GradleTestVersions#versions")
    fun `should put environment variables with trace and span IDs with config cache param enabled`(
        gradleVersion: String,
        @TempDir projectRootDirPath: Path,
    ) {
        val buildFileContents =
            """
            ${baseBuildFileContents()}

            openTelemetryBuild {
                endpoint = 'http://localhost:${jaegerContainer.getMappedPort(oltpGrpcPort)}'
                supportConfigCache = true
                taskTraceEnvironmentEnabled = true
                
                traceViewUrl = "http://localhost:16686/trace/{traceId}"
            }
            
            task printEnv(type: Exec) {
                commandLine "/bin/bash", "script.sh"
            }
            """.trimIndent()
        File(projectRootDirPath.toFile(), "build.gradle").writeText(buildFileContents)

        val scriptFileContents = "echo traceid: \$TRACE_ID spanid: \$SPAN_ID".trimIndent()
        File(projectRootDirPath.toFile(), "script.sh").writeText(scriptFileContents)

        val buildResult =
            GradleRunner.create()
                .withProjectDir(projectRootDirPath.toFile())
                .withArguments("printEnv", "--info", "--stacktrace")
                .withGradleVersion(gradleVersion)
                .withPluginClasspath()
                .build()

        expectThat(buildResult.task(":printEnv")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val traceId = extractTraceId(buildResult.output)
        val trace = fetchTrace(traceId = traceId, verifyRootSpanId = true)
        expectThat(trace.data).hasSize(1)

        val rootSpanId = trace.data.first().spans.find { it.isRoot() }?.spanID
        expectThat(rootSpanId).isNotNull()

        expectThat(buildResult.output).contains("traceid: $traceId spanid: $rootSpanId")

        val orderedSpansNamesWithDepth = fetchSpanNamesWithDepth(traceId)
        assertLinesMatch(
            listOf(
                " junit-\\d+-build",
                "> :printEnv",
            ),
            orderedSpansNamesWithDepth,
        )
    }

    @Disabled("Doesn't work yet with config cache enabled")
    @ParameterizedTest
    @MethodSource("com.atkinsondev.opentelemetry.build.util.GradleTestVersions#versions")
    fun `should put environment variables with trace and span IDs with config cache enabled`(
        gradleVersion: String,
        @TempDir projectRootDirPath: Path,
    ) {
        val buildFileContents =
            """
            ${baseBuildFileContents()}

            openTelemetryBuild {
                endpoint = 'http://localhost:${jaegerContainer.getMappedPort(oltpGrpcPort)}'
                supportConfigCache = true
                taskTraceEnvironmentEnabled = true
                
                traceViewUrl = "http://localhost:16686/trace/{traceId}"
            }
            
            task printEnv(type: Exec) {
                commandLine "/bin/bash", "script.sh"
            }
            """.trimIndent()
        File(projectRootDirPath.toFile(), "build.gradle").writeText(buildFileContents)

        val scriptFileContents = "echo traceid: \$TRACE_ID spanid: \$SPAN_ID".trimIndent()
        File(projectRootDirPath.toFile(), "script.sh").writeText(scriptFileContents)

        val buildResult =
            GradleRunner.create()
                .withProjectDir(projectRootDirPath.toFile())
                .withArguments("printEnv", "--configuration-cache", "--info", "--stacktrace")
                .withEnvironment(mapOf("JAVA_OPTS" to "--add-opens=java.base/java.util=ALL-UNNAMED"))
                .withGradleVersion(gradleVersion)
                .withPluginClasspath()
                .build()

        expectThat(buildResult.task(":printEnv")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val traceId = extractTraceId(buildResult.output)
        val trace = fetchTrace(traceId = traceId, verifyRootSpanId = true)
        expectThat(trace.data).hasSize(1)

        val rootSpanId = trace.data.first().spans.find { it.isRoot() }?.spanID
        expectThat(rootSpanId).isNotNull()

        expectThat(buildResult.output).contains("traceid: $traceId spanid: $rootSpanId")

        val orderedSpansNamesWithDepth = fetchSpanNamesWithDepth(traceId)
        assertLinesMatch(
            listOf(
                " junit-\\d+-build",
                "> :printEnv",
            ),
            orderedSpansNamesWithDepth,
        )
    }
}
