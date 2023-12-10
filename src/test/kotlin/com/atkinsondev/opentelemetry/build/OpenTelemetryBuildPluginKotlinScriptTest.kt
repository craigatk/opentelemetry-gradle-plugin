package com.atkinsondev.opentelemetry.build

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import org.awaitility.Awaitility
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import java.io.File
import java.nio.file.Path

@WireMockTest
class OpenTelemetryBuildPluginKotlinScriptTest {
    @ParameterizedTest
    @ValueSource(strings = ["7.6.3", "8.5"])
    fun `should send data to OpenTelemetry when using a Kotlin build script`(
        gradleVersion: String,
        wmRuntimeInfo: WireMockRuntimeInfo,
        @TempDir projectRootDirPath: Path,
    ) {
        val wiremockBaseUrl = wmRuntimeInfo.httpBaseUrl

        val buildFileContents =
            """
            ${baseKotlinBuildFileContents()}
            
             configure<com.atkinsondev.opentelemetry.build.OpenTelemetryBuildPluginExtension> {
                endpoint.set("$wiremockBaseUrl/otel")
                exporterMode.set(com.atkinsondev.opentelemetry.build.OpenTelemetryExporterMode.HTTP)
            }
            """.trimIndent()

        File(projectRootDirPath.toFile(), "build.gradle.kts").writeText(buildFileContents)

        createSrcDirectoryAndClassFile(projectRootDirPath)
        createTestDirectoryAndClassFile(projectRootDirPath)

        WireMock.stubFor(WireMock.post("/otel").willReturn(WireMock.ok()))

        val buildResult =
            GradleRunner.create()
                .withProjectDir(projectRootDirPath.toFile())
                .withArguments("test", "--info", "--stacktrace")
                .withPluginClasspath()
                .withGradleVersion(gradleVersion)
                .build()

        expectThat(buildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        Awaitility.await().untilAsserted {
            val otelRequests = WireMock.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo("/otel")))

            val otelRequestBodies = otelRequests.map { it.bodyAsString }

            val rootSpanName = "gradle-build"
            expectThat(otelRequestBodies.find { it.contains(rootSpanName) }).isNotNull()
        }

        Awaitility.await().untilAsserted {
            val otelRequests = WireMock.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo("/otel")))

            val otelRequestBodies = otelRequests.map { it.bodyAsString }

            val testSpanName = ":test"
            expectThat(otelRequestBodies.find { it.contains(testSpanName) }).isNotNull()
        }
    }
}
