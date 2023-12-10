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
import strikt.assertions.any
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty
import java.io.File
import java.nio.file.Path

@WireMockTest
class OpenTelemetryBuildPluginZipkinTest {
    @Test
    fun `should send data to OpenTelemetry with Zipkin`(
        wmRuntimeInfo: WireMockRuntimeInfo,
        @TempDir projectRootDirPath: Path,
    ) {
        val wiremockBaseUrl = wmRuntimeInfo.httpBaseUrl

        val buildFileContents =
            """
            ${baseBuildFileContents()}
            
            openTelemetryBuild {
                endpoint = '$wiremockBaseUrl/zipkin'
                
                exporterMode = com.atkinsondev.opentelemetry.build.OpenTelemetryExporterMode.ZIPKIN
            }
            """.trimIndent()

        File(projectRootDirPath.toFile(), "build.gradle").writeText(buildFileContents)

        createSrcDirectoryAndClassFile(projectRootDirPath)
        createTestDirectoryAndClassFile(projectRootDirPath)

        stubFor(post("/zipkin").willReturn(ok()))

        val buildResult =
            GradleRunner.create()
                .withProjectDir(projectRootDirPath.toFile())
                .withArguments("test", "--info", "--stacktrace")
                .withPluginClasspath()
                .build()

        expectThat(buildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        await().untilAsserted {
            val zipkinRequests = findAll(postRequestedFor(urlEqualTo("/zipkin")))

            expectThat(zipkinRequests).isNotEmpty().any {
                get { bodyAsString }.contains("gradle-builds")
            }
        }
    }
}
