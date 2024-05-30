package com.atkinsondev.opentelemetry.build

import com.atkinsondev.opentelemetry.build.util.BuildOutputParser.extractTraceId
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertLinesMatch
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import strikt.api.expectThat
import strikt.assertions.*
import java.io.File
import java.nio.file.Path

class OpenTelemetryBuildPluginSpansIntegrationTest : JaegerIntegrationTestCase() {
    override val healthCheckPort = 14270
    override val oltpGrpcPort = 4400
    override val queryPort = 16687

    @Test
    fun `check spans`(
        @TempDir projectRootDirPath: Path,
    ) {
        val buildFileContents =
            """
            ${baseBuildFileContents()}

            openTelemetryBuild {
                endpoint = 'http://localhost:${jaegerContainer.getMappedPort(oltpGrpcPort)}'
            }
            """.trimIndent()

        File(projectRootDirPath.toFile(), "build.gradle").writeText(buildFileContents)

        createSrcDirectoryAndClassFile(projectRootDirPath)
        createTestDirectoryAndClassFile(projectRootDirPath)

        val buildResult =
            GradleRunner.create()
                .withProjectDir(projectRootDirPath.toFile())
                .withArguments("test", "--info", "--stacktrace")
                .withPluginClasspath()
                .build()

        expectThat(buildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        // Parse trace ID from build output
        val traceId = extractTraceId(buildResult.output)

        val orderedSpansNamesWithDepth = fetchSpansWithDepth(traceId)

        // Use assertLinesMatch, as it has nice support for regexes
        assertLinesMatch(
            listOf(
                " junit\\d+-build",
                "> :compileKotlin",
                "> :processResources",
                "> :processTestResources",
                "> :compileJava",
                "> :classes",
                "> :compileTestKotlin",
                "> :compileTestJava",
                "> :testClasses",
                "> :test",
                ">> Gradle Test Executor \\d",
                ">>> FooTest",
                ">>>> foo should return bar()",
            ),
            orderedSpansNamesWithDepth,
        )
    }

    @Test
    fun `check spans test not nested`(
        @TempDir projectRootDirPath: Path,
    ) {
        val buildFileContents =
            """
            ${baseBuildFileContents()}

            openTelemetryBuild {
                endpoint = 'http://localhost:${jaegerContainer.getMappedPort(oltpGrpcPort)}'
                nestedTestSpans = false
            }
            """.trimIndent()

        File(projectRootDirPath.toFile(), "build.gradle").writeText(buildFileContents)

        createSrcDirectoryAndClassFile(projectRootDirPath)
        createTestDirectoryAndClassFile(projectRootDirPath)

        val buildResult =
            GradleRunner.create()
                .withProjectDir(projectRootDirPath.toFile())
                .withArguments("test", "--info", "--stacktrace")
                .withPluginClasspath()
                .build()

        expectThat(buildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        // Parse trace ID from build output
        val traceId = extractTraceId(buildResult.output)

        val orderedSpansNamesWithDepth = fetchSpansWithDepth(traceId)

        // Use assertLinesMatch, as it has nice support for regexes
        assertLinesMatch(
            listOf(
                " junit\\d+-build",
                "> :compileKotlin",
                "> :processResources",
                "> :processTestResources",
                "> :compileJava",
                "> :classes",
                "> :compileTestKotlin",
                "> :compileTestJava",
                "> :testClasses",
                "> :test",
                ">> FooTest foo should return bar()",
            ),
            orderedSpansNamesWithDepth,
        )
    }
}
