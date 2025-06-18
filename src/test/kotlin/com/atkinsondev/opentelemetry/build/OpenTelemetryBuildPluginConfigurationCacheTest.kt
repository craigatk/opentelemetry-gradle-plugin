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
            .any {
                get { key }.isEqualTo("task.outcome")
                get { strValue }.isEqualTo("EXECUTED")
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

    @ParameterizedTest
    @MethodSource("com.atkinsondev.opentelemetry.build.util.GradleTestVersions#versions")
    fun `should publish spans when using config-cache compatible listener with task outcomes`(
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

        val buildRunner =
            GradleRunner.create()
                .withProjectDir(projectRootDirPath.toFile())
                .withArguments("test", "--info", "--stacktrace", "--build-cache")
                .withGradleVersion(gradleVersion)
                .withPluginClasspath()

        val initialBuildResult = buildRunner.build()

        expectThat(initialBuildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        // Parse trace ID from build output
        val traceId = extractTraceId(initialBuildResult.output)

        expectThat(initialBuildResult.output).contains("OpenTelemetry build trace http://localhost:16686/trace/$traceId")

        val rootSpans = fetchRootSpans(traceId)
        expectThat(rootSpans.first().operationName).matches("junit-\\d+-build".toRegex())

        val taskSpans = rootSpans.first().children

        val expectedTaskOutcomes = mapOf(
            ":checkKotlinGradlePluginConfigurationErrors" to "SKIPPED",
            ":compileKotlin" to "EXECUTED",
            ":processResources" to "NO-SOURCE",
            ":processTestResources" to "NO-SOURCE",
            ":compileJava" to "NO-SOURCE",
            ":classes" to "UP-TO-DATE",
            ":jar" to "EXECUTED",
            ":compileTestKotlin" to "EXECUTED",
            ":compileTestJava" to "NO-SOURCE",
            ":testClasses" to "UP-TO-DATE",
            ":test" to "EXECUTED",
        )

        val taskSpanNames = taskSpans.map { it.operationName }.sorted()

        // Task execution order of :jar and :classes can be switched, so can :processResources and :processTestResources.
        // Sorting the task list consistently verifies that all expected tasks were in the task graph, but not necessarily executed in the same order.
        assertLinesMatch(
            expectedTaskOutcomes.keys.sorted(),
            taskSpanNames,
        )

        taskSpans.forEach { taskSpan ->
            taskSpan.assertStrAttributeEquals("task.outcome", expectedTaskOutcomes[taskSpan.operationName])
        }

        taskSpans.first { it.operationName == ":compileKotlin" }
            .assertBoolAttributeEquals("task.is_incremental", false)

        taskSpans.first { it.operationName == ":compileTestJava" }
            .assertBoolAttributeEquals("task.is_incremental", null)

        // Delete the project directory so that the build cache is used, then run the task again
        File(projectRootDirPath.toFile(), "build").deleteRecursively()
        val secondBuildResult = buildRunner.build()

        expectThat(secondBuildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.FROM_CACHE)

        // Parse trace ID from build output
        val secondTraceId = extractTraceId(secondBuildResult.output)
        val secondTaskSpans = fetchRootSpans(secondTraceId).first().children

        val expectedSecondTaskOutcomes = mapOf(
            ":checkKotlinGradlePluginConfigurationErrors" to "SKIPPED",
            ":compileKotlin" to "FROM-CACHE",
            ":processResources" to "NO-SOURCE",
            ":processTestResources" to "NO-SOURCE",
            ":compileJava" to "NO-SOURCE",
            ":classes" to "UP-TO-DATE",
            ":jar" to "EXECUTED",
            ":compileTestKotlin" to "FROM-CACHE",
            ":compileTestJava" to "NO-SOURCE",
            ":testClasses" to "UP-TO-DATE",
            ":test" to "FROM-CACHE",
        )

        val secondTaskSpanNames = secondTaskSpans.map { it.operationName }.sorted()

        // Task execution order of :jar and :classes can be switched, so can :processResources and :processTestResources.
        // Sorting the task list consistently verifies that all expected tasks were in the task graph, but not necessarily executed in the same order.
        assertLinesMatch(
            expectedSecondTaskOutcomes.keys.sorted(),
            secondTaskSpanNames,
        )

        secondTaskSpans.forEach { taskSpan ->
            taskSpan.assertStrAttributeEquals("task.outcome", expectedSecondTaskOutcomes[taskSpan.operationName])
        }

        expectThat(
            secondTaskSpans
                .first { it.operationName == ":jar" }
                .tags
                .first { it.key == "task.execution_reasons" }
                .strValue
                ?.replace(Regex("file.*\\.jar"), "file junit.jar")
        )
            .describedAs { ":jar span attribute `task.execution_reasons`" }
            .isEqualTo("Output property 'archiveFile' file junit.jar has been removed.")
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

    private fun ResponseSpan.assertStrAttributeEquals(attributeKey: String, expectedValue: String?) {
        expectThat(tags.first { it.key == attributeKey }.strValue)
            .describedAs { "$operationName span attribute `$attributeKey`" }
            .isEqualTo(expectedValue)
    }

    private fun ResponseSpan.assertBoolAttributeEquals(attributeKey: String, expectedValue: Boolean?) {
        expectThat(tags.first { it.key == attributeKey }.boolValue)
            .describedAs { "$operationName span attribute `$attributeKey`" }
            .isEqualTo(expectedValue)
    }
}
