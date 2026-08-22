package com.atkinsondev.opentelemetry.build

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import org.awaitility.Awaitility.await
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import strikt.api.expectThat
import strikt.assertions.*
import java.io.File
import java.nio.file.Path

@WireMockTest
class OpenTelemetryBuildPluginGlobalOpenTelemetryTest {
    @Test
    fun `should send spans created outside the plugin via GlobalOpenTelemetry`(
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

            // Stands in for init-script instrumentation that obtains a tracer from the global instance
            tasks.register("customSpan") {
                doLast {
                    def tracer = io.opentelemetry.api.GlobalOpenTelemetry.getTracer("init-script")
                    tracer.spanBuilder("outside-plugin-span").startSpan().end()
                }
            }
            """.trimIndent()

        File(projectRootDirPath.toFile(), "build.gradle").writeText(buildFileContents)

        createSrcDirectoryAndClassFile(projectRootDirPath)

        stubFor(post("/otel").willReturn(ok()))

        val buildResult =
            GradleRunner
                .create()
                .withProjectDir(projectRootDirPath.toFile())
                .withArguments("customSpan", "--info", "--stacktrace")
                .withPluginClasspath()
                .build()

        expectThat(buildResult.task(":customSpan")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        await().untilAsserted {
            val otelRequestBodies = findAll(postRequestedFor(urlEqualTo("/otel"))).map { it.bodyAsString }

            expectThat(otelRequestBodies.find { it.contains("outside-plugin-span") }).isNotNull()
        }
    }
}
