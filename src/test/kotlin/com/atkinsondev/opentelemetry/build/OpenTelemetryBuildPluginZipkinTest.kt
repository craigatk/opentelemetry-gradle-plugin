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

class OpenTelemetryBuildPluginZipkinTest : ZipkinIntegrationTestCase() {
    override val zipkinPort = 9411

    @Test
    fun `should send data to OpenTelemetry with Zipkin`(
        @TempDir projectRootDirPath: Path,
    ) {
        val buildFileContents =
            """
            ${baseBuildFileContents()}

            openTelemetryBuild {
                endpoint = '${zipkinSpansEndpoint()}'

                exporterMode = com.atkinsondev.opentelemetry.build.OpenTelemetryExporterMode.ZIPKIN
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
                .withPluginClasspath()
                .build()

        expectThat(buildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        // Parse trace ID from build output
        val traceId = extractTraceId(buildResult.output)

        val rootSpans = fetchRootSpans(traceId)

        // Verify the spans were actually accepted and indexed by a real Zipkin backend, with the
        // expected parent/child structure - not just that some bytes containing "gradle-builds" were POSTed.
        expectThat(rootSpans.flatMap { it.allServiceNames() }).isNotEmpty().all { isEqualTo("gradle-builds") }

        val orderedSpansNamesWithDepth = fetchSpanNamesWithDepth(traceId)

        // Zipkin's span model lower-cases span names (see https://zipkin.io/pages/data_model.html),
        // and the OpenTelemetry Zipkin exporter honors that - unlike the other exporters, which preserve case.
        assertLinesMatch(
            listOf(
                " junit-\\d+-build",
                "> :checkkotlingradlepluginconfigurationerrors",
                "> :compilekotlin",
                "> :processresources",
                "> :processtestresources",
                "> :compilejava",
                "> (:jar|:classes)",
                "> (:jar|:classes)",
                "> :compiletestkotlin",
                "> :compiletestjava",
                "> :testclasses",
                "> :test",
                ">> gradle test executor \\d+",
                ">>> bartest",
                ">>>> bar should not return baz()",
                ">>>> bar should return foo()",
                ">>> footest",
                ">>>> foo should not return baz()",
                ">>>> foo should return bar()",
            ),
            orderedSpansNamesWithDepth,
        )
    }
}
