package com.atkinsondev.opentelemetry.build

import com.atkinsondev.opentelemetry.build.util.BuildOutputParser.extractTraceId
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertLinesMatch
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import strikt.api.expectThat
import strikt.assertions.*
import java.io.File
import java.nio.file.Path

class OpenTelemetryBuildPluginConfigurationCacheTest : JaegerIntegrationTestCase() {
    override val healthCheckPort = 14271
    override val oltpGrpcPort = 4401
    override val queryPort = 16688

    @ParameterizedTest
    @MethodSource("com.atkinsondev.opentelemetry.build.util.GradleTestVersions#versions")
    fun `should publish spans when using config-cache compatible listener with plugin config param`(
        gradleVersion: String,
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
                .withGradleVersion(gradleVersion)
                .withPluginClasspath()
                .build()

        expectThat(buildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        // Parse trace ID from build output
        val traceId = extractTraceId(buildResult.output)

        expectThat(buildResult.output).contains("OpenTelemetry build trace http://localhost:16686/trace/$traceId")

        val rootSpans = fetchRootSpans(traceId)
        expectThat(rootSpans.first().operationName).matches("junit-\\d+-build".toRegex())

        val taskSpans = rootSpans.first().children

        val taskSpanNames = taskSpans.map { it.operationName }
        assertLinesMatch(
            listOf(
                ":checkKotlinGradlePluginConfigurationErrors",
                ":compileKotlin",
                ":processResources",
                ":processTestResources",
                ":compileJava",
                "(:jar|:classes)",
                "(:jar|:classes)",
                ":compileTestKotlin",
                ":compileTestJava",
                ":testClasses",
                ":test",
            ),
            taskSpanNames,
        )

        val testTaskSpan = taskSpans.find { it.operationName == ":test" } ?: throw AssertionError("Test task span not found")

        val testExecutorSpan = testTaskSpan.children.first()
        expectThat(testExecutorSpan.operationName).matches("Gradle Test Executor \\d+".toRegex())

        val testClassSpans = testExecutorSpan.children
        expectThat(testClassSpans).hasSize(2).map { it.operationName }.contains("BarTest", "FooTest")

        expectThat(testClassSpans.find { it.operationName == "BarTest" })
            .isNotNull()
            .get { children }
            .hasSize(2)
            .map { it.operationName }
            .contains("bar should not return baz()", "bar should return foo()")

        expectThat(testClassSpans.find { it.operationName == "FooTest" })
            .isNotNull()
            .get { children }
            .hasSize(2)
            .map { it.operationName }
            .contains("foo should not return baz()", "foo should return bar()")
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
                "> (:jar|:classes)",
                "> (:jar|:classes)",
                "> :compileTestKotlin",
                "> :compileTestJava",
                "> (:testClasses|:test)",
                "> (:testClasses|:test)",
                ">> FooTest foo should return bar but will fail()",
            ),
            orderedSpansNamesWithDepth,
        )

        val rootSpans = fetchRootSpans(traceId)
        val taskSpans = rootSpans.flatMap { it.children }
        expectThat(taskSpans).any { get { operationName }.isEqualTo(":test") }

        val testTaskSpan = taskSpans.find { it.operationName == ":test" } ?: throw AssertionError("Test task span not found")

        expectThat(testTaskSpan.tags)
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

        val testCaseSpan = testTaskSpan.children.first()
        expectThat(testCaseSpan.operationName).isEqualTo("FooTest foo should return bar but will fail()")

        expectThat(testCaseSpan.tags)
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

        val rootSpans = fetchRootSpans(traceId)

        expectThat(rootSpans.first().operationName).matches("junit-\\d+-build".toRegex())

        val taskSpans = rootSpans.first().children

        val testTaskSpan = taskSpans.find { it.operationName == ":test" } ?: throw AssertionError("Test task span not found")

        testTaskSpan.expectThatSpanIsAfter(":processTestResources", taskSpans)
        testTaskSpan.expectThatSpanIsAfter(":compileTestKotlin", taskSpans)

        val testExecutorSpan = testTaskSpan.children.first()
        expectThat(testExecutorSpan.operationName).matches("Gradle Test Executor \\d+".toRegex())

        val testClassSpans = testExecutorSpan.children
        expectThat(testClassSpans).hasSize(2).map { it.operationName }.contains("BarTest", "FooTest")

        expectThat(testClassSpans.find { it.operationName == "BarTest" })
            .isNotNull()
            .get { children }
            .hasSize(2)
            .map { it.operationName }
            .contains("bar should not return baz()", "bar should return foo()")

        expectThat(testClassSpans.find { it.operationName == "FooTest" })
            .isNotNull()
            .get { children }
            .hasSize(2)
            .map { it.operationName }
            .contains("foo should not return baz()", "foo should return bar()")
    }

    @Test
    fun `should publish spans when using config-cache compatible listener with config-cache enabled and running help task`(
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
                .withArguments("help", "--configuration-cache", "--info", "--stacktrace")
                .withEnvironment(mapOf("JAVA_OPTS" to "--add-opens=java.base/java.util=ALL-UNNAMED"))
                .withPluginClasspath()
                .build()

        expectThat(buildResult.task(":help")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        println(buildResult.output)

        expectThat(buildResult.output).contains("0 problems were found storing the configuration cache")

        // Parse trace ID from build output
        val traceId = extractTraceId(buildResult.output)

        val orderedSpansWithDepth = fetchSpansWithDepth(traceId)

        expectThat(orderedSpansWithDepth.first().operationName).matches("junit-\\d+-build".toRegex())
        expectThat(orderedSpansWithDepth).any { get { operationName }.isEqualTo(":help") }
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

        val rootSpans = fetchRootSpans(traceId)

        expectThat(rootSpans.first().operationName).matches("junit-\\d+-build".toRegex())

        val taskSpans = rootSpans.first().children

        val testTaskSpan = taskSpans.find { it.operationName == ":test" } ?: throw AssertionError("Test task span not found")

        testTaskSpan.expectThatSpanIsAfter(":processTestResources", taskSpans)
        testTaskSpan.expectThatSpanIsAfter(":compileTestKotlin", taskSpans)

        val testCaseSpans = testTaskSpan.children
        expectThat(testCaseSpans)
            .hasSize(4)
            .map { it.operationName }
            .contains("BarTest bar should not return baz()", "BarTest bar should return foo()", "FooTest foo should not return baz()", "FooTest foo should return bar()")
    }
}
