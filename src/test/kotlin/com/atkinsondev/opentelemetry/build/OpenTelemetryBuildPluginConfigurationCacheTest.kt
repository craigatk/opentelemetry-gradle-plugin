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

class OpenTelemetryBuildPluginConfigurationCacheTest : JaegerIntegrationTestCase() {
    override val healthCheckPort = 14271
    override val oltpGrpcPort = 4401
    override val queryPort = 16688

    @Test
    fun `should publish spans when using config-cache compatible listener with plugin config param`(
        @TempDir projectRootDirPath: Path,
    ) {
        val buildFileContents =
            """
            ${baseBuildFileContents()}

            openTelemetryBuild {
                endpoint = 'http://localhost:${jaegerContainer.getMappedPort(oltpGrpcPort)}'
                supportConfigCache = true
                
                traceViewUrl = "http://localhost:16686/trace/{traceId}"
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

        expectThat(buildResult.output).contains("OpenTelemetry build trace http://localhost:16686/trace/$traceId")

        val orderedSpansNamesWithDepth = fetchSpanNamesWithDepth(traceId)

        assertLinesMatch(
            listOf(
                " junit-\\d+-build",
                "> :checkKotlinGradlePluginConfigurationErrors",
                "> :compileKotlin",
                "> :processResources",
                "> :processTestResources",
                "> :compileJava",
                "> :classes",
                "> :jar",
                "> :compileTestKotlin",
                "> :compileTestJava",
                "> :testClasses",
                "> :test",
                ">> Gradle Test Executor \\d+",
                ">>> BarTest",
                ">>>> bar should not return baz()",
                ">>>> bar should return foo()",
                ">>> FooTest",
                ">>>> foo should not return baz()",
                ">>>> foo should return bar()",
            ),
            orderedSpansNamesWithDepth,
        )
    }

    @Test
    fun `should publish spans when using config-cache compatible listener with plugin config param and failing test`(
        @TempDir projectRootDirPath: Path,
    ) {
        val buildFileContents =
            """
            ${baseBuildFileContents()}

            openTelemetryBuild {
                endpoint = 'http://localhost:${jaegerContainer.getMappedPort(oltpGrpcPort)}'
                supportConfigCache = true
                nestedTestSpans = false
                
                traceViewUrl = "http://localhost:16686/trace/{traceId}"
            }
            """.trimIndent()

        File(projectRootDirPath.toFile(), "build.gradle").writeText(buildFileContents)

        createSrcDirectoryAndClassFile(projectRootDirPath)
        createTestDirectoryAndFailingClassFile(projectRootDirPath)

        val buildResult =
            GradleRunner.create()
                .withProjectDir(projectRootDirPath.toFile())
                .withArguments("test", "--info", "--stacktrace")
                .withPluginClasspath()
                .buildAndFail()

        expectThat(buildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.FAILED)

        // Parse trace ID from build output
        val traceId = extractTraceId(buildResult.output)

        expectThat(buildResult.output).contains("OpenTelemetry build trace http://localhost:16686/trace/$traceId")

        val orderedSpansNamesWithDepth = fetchSpanNamesWithDepth(traceId)

        assertLinesMatch(
            listOf(
                " junit-\\d+-build",
                "> :checkKotlinGradlePluginConfigurationErrors",
                "> :compileKotlin",
                "> :processResources",
                "> :processTestResources",
                "> :compileJava",
                "> :classes",
                "> :jar",
                "> :compileTestKotlin",
                "> :compileTestJava",
                "> :testClasses",
                "> :test",
                ">> FooTest foo should return bar but will fail()",
            ),
            orderedSpansNamesWithDepth,
        )

        val rootSpans = fetchRootSpans(traceId)
        val taskSpans = rootSpans.flatMap { it.children }
        expectThat(taskSpans).any { get { operationName }.isEqualTo(":test") }

        val testTaskSpan = taskSpans.find { it.operationName == ":test" }
        expectThat(testTaskSpan).isNotNull()

        expectThat(testTaskSpan?.tags)
            .isNotNull()
            .any {
                get { key }.isEqualTo("error")
                get { boolValue }.isEqualTo(true)
            }
            .any {
                get { key }.isEqualTo("error_message")
                get { strValue }.isNotNull().contains("Execution failed for task ':test'")
            }
            .any {
                get { key }.isEqualTo("task.path")
                get { strValue }.isEqualTo(":test")
            }

        val testCaseSpan = testTaskSpan?.children?.first()
        expectThat(testCaseSpan?.operationName).isEqualTo("FooTest foo should return bar but will fail()")

        expectThat(testCaseSpan?.tags)
            .isNotNull()
            .any {
                get { key }.isEqualTo("error")
                get { boolValue }.isEqualTo(true)
            }
            .any {
                get { key }.isEqualTo("error_message")
                get { strValue }.isNotNull().contains("Assertion failed")
            }
            .any {
                get { key }.isEqualTo("test.failure.stacktrace")
                get { strValue }.isNotNull().contains("FooTest.foo should return bar but will fail")
            }
    }

    @Test
    fun `should publish spans when using config-cache compatible listener with config-cache enabled`(
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
                .withArguments("test", "--configuration-cache", "--info", "--stacktrace")
                .withEnvironment(mapOf("JAVA_OPTS" to "--add-opens=java.base/java.util=ALL-UNNAMED"))
                .withPluginClasspath()
                .build()

        expectThat(buildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        println(buildResult.output)

        expectThat(buildResult.output).contains("0 problems were found storing the configuration cache")

        // Parse trace ID from build output
        val traceId = extractTraceId(buildResult.output)

        val orderedSpansWithDepth = fetchSpansWithDepth(traceId)

        expectThat(orderedSpansWithDepth.first().operationName).matches("junit-\\d+-build".toRegex())

        orderedSpansWithDepth.find { it.operationName == ":test" }?.expectThatSpanIsAfter(":processTestResources", orderedSpansWithDepth)
        orderedSpansWithDepth.find { it.operationName == ":test" }?.expectThatSpanIsAfter(":compileTestKotlin", orderedSpansWithDepth)

        val testTaskIndex = orderedSpansWithDepth.indexOfFirst { it.operationName == ":test" }

        val orderedSpansNamesWithDepth = fetchSpanNamesWithDepth(traceId)
        val testSpanNames = orderedSpansNamesWithDepth.subList(testTaskIndex, orderedSpansNamesWithDepth.size)

        assertLinesMatch(
            listOf(
                "> :test",
                ">> Gradle Test Executor \\d+",
                ">>> BarTest",
                ">>>> bar should not return baz()",
                ">>>> bar should return foo()",
                ">>> FooTest",
                ">>>> foo should not return baz()",
                ">>>> foo should return bar()",
            ),
            testSpanNames,
        )
    }

    @Test
    fun `should publish spans when using config-cache compatible listener with config-cache enabled and not nested test spans`(
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
                .withArguments("test", "--configuration-cache", "--info", "--stacktrace")
                .withEnvironment(mapOf("JAVA_OPTS" to "--add-opens=java.base/java.util=ALL-UNNAMED"))
                .withPluginClasspath()
                .build()

        expectThat(buildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        println(buildResult.output)

        expectThat(buildResult.output).contains("0 problems were found storing the configuration cache")

        // Parse trace ID from build output
        val traceId = extractTraceId(buildResult.output)

        val orderedSpansWithDepth = fetchSpansWithDepth(traceId)

        expectThat(orderedSpansWithDepth.first().operationName).matches("junit-\\d+-build".toRegex())

        orderedSpansWithDepth.find { it.operationName == ":test" }?.expectThatSpanIsAfter(":processTestResources", orderedSpansWithDepth)
        orderedSpansWithDepth.find { it.operationName == ":test" }?.expectThatSpanIsAfter(":compileTestKotlin", orderedSpansWithDepth)

        val testTaskIndex = orderedSpansWithDepth.indexOfFirst { it.operationName == ":test" }

        val orderedSpansNamesWithDepth = fetchSpanNamesWithDepth(traceId)
        val testSpanNames = orderedSpansNamesWithDepth.subList(testTaskIndex, orderedSpansNamesWithDepth.size)

        assertLinesMatch(
            listOf(
                "> :test",
                ">> BarTest bar should not return baz()",
                ">> BarTest bar should return foo()",
                ">> FooTest foo should not return baz()",
                ">> FooTest foo should return bar()",
            ),
            testSpanNames,
        )
    }
}
