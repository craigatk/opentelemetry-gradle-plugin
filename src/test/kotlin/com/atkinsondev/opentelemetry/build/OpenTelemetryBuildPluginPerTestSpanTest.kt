package com.atkinsondev.opentelemetry.build

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import org.awaitility.Awaitility
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import java.io.File
import java.nio.file.Path

@WireMockTest
class OpenTelemetryBuildPluginPerTestSpanTest {
    @Test
    fun `should send span for passing test`(
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

        stubFor(WireMock.post("/otel").willReturn(ok()))

        val buildResult =
            GradleRunner.create()
                .withProjectDir(projectRootDirPath.toFile())
                .withArguments("test", "--info", "--stacktrace")
                .withPluginClasspath()
                .build()

        expectThat(buildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        Awaitility.await().untilAsserted {
            val otelRequests = WireMock.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo("/otel")))

            val otelRequestBodies = otelRequests.map { it.bodyAsString }

            val testMethodName = "foo should return bar"
            expectThat(otelRequestBodies.find { it.contains(testMethodName) }).isNotNull()

            val testSuccessResultName = "SUCCESS"
            expectThat(otelRequestBodies.find { it.contains(testSuccessResultName) }).isNotNull()
        }
    }

    @Test
    fun `should send span for failing test`(
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

        stubFor(WireMock.post("/otel").willReturn(ok()))

        createSrcDirectoryAndClassFile(projectRootDirPath)
        createTestDirectoryAndFailingClassFile(projectRootDirPath)

        stubFor(WireMock.post("/otel").willReturn(ok()))

        val buildResult =
            GradleRunner.create()
                .withProjectDir(projectRootDirPath.toFile())
                .withArguments("test", "--info", "--stacktrace")
                .withPluginClasspath()
                .buildAndFail()

        expectThat(buildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.FAILED)

        Awaitility.await().untilAsserted {
            val otelRequests = WireMock.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo("/otel")))

            val otelRequestBodies = otelRequests.map { it.bodyAsString }

            val testMethodName = "foo should return bar but will fail"
            expectThat(otelRequestBodies.find { it.contains(testMethodName) }).isNotNull()

            val testFailureResultName = "FAILURE"
            expectThat(otelRequestBodies.find { it.contains(testFailureResultName) }).isNotNull()
        }
    }
}
