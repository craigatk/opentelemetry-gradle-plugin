package com.atkinsondev.opentelemetry.build

import com.atkinsondev.opentelemetry.build.OpenTelemetryInit.Companion.userAgentValue
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
class OpenTelemetryBuildPluginTest {

    @Test
    fun `should send data to OpenTelemetry with HTTP`(wmRuntimeInfo: WireMockRuntimeInfo, @TempDir projectRootDirPath: Path) {
        val wiremockBaseUrl = wmRuntimeInfo.httpBaseUrl

        val buildFileContents = """
            ${baseBuildFileContents()}
            
            openTelemetryBuild {
                endpoint = '$wiremockBaseUrl/otel'
                exporterMode = com.atkinsondev.opentelemetry.build.OpenTelemetryExporterMode.HTTP
            }
        """.trimIndent()

        File(projectRootDirPath.toFile(), "build.gradle").writeText(buildFileContents)

        createSrcDirectoryAndClassFile(projectRootDirPath)
        createTestDirectoryAndClassFile(projectRootDirPath)

        stubFor(post("/otel").willReturn(ok()))

        val buildResult = GradleRunner.create()
            .withProjectDir(projectRootDirPath.toFile())
            .withArguments("test", "--info", "--stacktrace")
            .withPluginClasspath()
            .build()

        expectThat(buildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        await().untilAsserted {
            val otelRequests = findAll(postRequestedFor(urlEqualTo("/otel")))

            val otelRequestBodies = otelRequests.map { it.bodyAsString }

            val rootSpanName = "gradle-build"
            expectThat(otelRequestBodies.find { it.contains(rootSpanName) }).isNotNull()
        }

        await().untilAsserted {
            val otelRequests = findAll(postRequestedFor(urlEqualTo("/otel")))

            val otelRequestBodies = otelRequests.map { it.bodyAsString }

            val testSpanName = ":test"
            expectThat(otelRequestBodies.find { it.contains(testSpanName) }).isNotNull()
        }
    }

    @Test
    fun `should include custom SDK name in OTel payload`(wmRuntimeInfo: WireMockRuntimeInfo, @TempDir projectRootDirPath: Path) {
        val wiremockBaseUrl = wmRuntimeInfo.httpBaseUrl

        val buildFileContents = """
            ${baseBuildFileContents()}
            
            openTelemetryBuild {
                endpoint = '$wiremockBaseUrl/otel'
                exporterMode = com.atkinsondev.opentelemetry.build.OpenTelemetryExporterMode.HTTP
            }
        """.trimIndent()

        File(projectRootDirPath.toFile(), "build.gradle").writeText(buildFileContents)

        createSrcDirectoryAndClassFile(projectRootDirPath)
        createTestDirectoryAndClassFile(projectRootDirPath)

        stubFor(post("/otel").willReturn(ok()))

        val buildResult = GradleRunner.create()
            .withProjectDir(projectRootDirPath.toFile())
            .withArguments("test", "--info", "--stacktrace")
            .withPluginClasspath()
            .build()

        expectThat(buildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        await().untilAsserted {
            val otelRequests = findAll(postRequestedFor(urlEqualTo("/otel")))

            val otelRequestBodies = otelRequests.map { it.bodyAsString }

            val customSdkName = "gradle-opentelemetry-build-plugin"
            expectThat(otelRequestBodies.find { it.contains(customSdkName) }).isNotNull()
        }
    }

    @Test
    fun `should include user agent HTTP header`(wmRuntimeInfo: WireMockRuntimeInfo, @TempDir projectRootDirPath: Path) {
        val wiremockBaseUrl = wmRuntimeInfo.httpBaseUrl

        val buildFileContents = """
            ${baseBuildFileContents()}
            
            openTelemetryBuild {
                endpoint = '$wiremockBaseUrl/otel'
                exporterMode = com.atkinsondev.opentelemetry.build.OpenTelemetryExporterMode.HTTP
            }
        """.trimIndent()

        File(projectRootDirPath.toFile(), "build.gradle").writeText(buildFileContents)

        createSrcDirectoryAndClassFile(projectRootDirPath)
        createTestDirectoryAndClassFile(projectRootDirPath)

        stubFor(post("/otel").willReturn(ok()))

        val buildResult = GradleRunner.create()
            .withProjectDir(projectRootDirPath.toFile())
            .withArguments("test", "--info", "--stacktrace")
            .withPluginClasspath()
            .build()

        expectThat(buildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        await().untilAsserted {
            val otelRequests = findAll(postRequestedFor(urlEqualTo("/otel")))

            val otelRequestUserAgentHeaders = otelRequests.map { it.header("User-Agent") }

            expectThat(otelRequestUserAgentHeaders).any {
                get { values() }.contains(userAgentValue)
            }
        }
    }

    @Test
    fun `should send data to OpenTelemetry with HTTP and headers`(wmRuntimeInfo: WireMockRuntimeInfo, @TempDir projectRootDirPath: Path) {
        val wiremockBaseUrl = wmRuntimeInfo.httpBaseUrl

        val buildFileContents = """
            ${baseBuildFileContents()}
            
            openTelemetryBuild {
                endpoint = '$wiremockBaseUrl/otel'
                headers = ["foo1": "bar1", "foo2": "bar2"]
                exporterMode = com.atkinsondev.opentelemetry.build.OpenTelemetryExporterMode.HTTP
            }
        """.trimIndent()

        File(projectRootDirPath.toFile(), "build.gradle").writeText(buildFileContents)

        createSrcDirectoryAndClassFile(projectRootDirPath)
        createTestDirectoryAndClassFile(projectRootDirPath)

        stubFor(post("/otel").willReturn(ok()))

        val buildResult = GradleRunner.create()
            .withProjectDir(projectRootDirPath.toFile())
            .withArguments("test", "--info", "--stacktrace")
            .withPluginClasspath()
            .build()

        expectThat(buildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        await().untilAsserted {
            val otelRequests = findAll(postRequestedFor(urlEqualTo("/otel")))

            expectThat(otelRequests).isNotEmpty()

            expectThat(otelRequests[0].header("foo1").firstValue()).isEqualTo("bar1")
            expectThat(otelRequests[0].header("foo2").firstValue()).isEqualTo("bar2")
        }
    }

    @Test
    fun `when test fails should send failure data`(wmRuntimeInfo: WireMockRuntimeInfo, @TempDir projectRootDirPath: Path) {
        val wiremockBaseUrl = wmRuntimeInfo.httpBaseUrl

        val buildFileContents = """
            ${baseBuildFileContents()}
            
            openTelemetryBuild {
                endpoint = '$wiremockBaseUrl/otel'
                exporterMode = com.atkinsondev.opentelemetry.build.OpenTelemetryExporterMode.HTTP
            }
        """.trimIndent()

        File(projectRootDirPath.toFile(), "build.gradle").writeText(buildFileContents)

        createSrcDirectoryAndClassFile(projectRootDirPath)
        createTestDirectoryAndFailingClassFile(projectRootDirPath)

        stubFor(post("/otel").willReturn(ok()))

        val buildResult = GradleRunner.create()
            .withProjectDir(projectRootDirPath.toFile())
            .withArguments("test", "--info", "--stacktrace")
            .withPluginClasspath()
            .buildAndFail()

        expectThat(buildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.FAILED)

        await().untilAsserted {
            val otelRequests = findAll(postRequestedFor(urlEqualTo("/otel")))

            val otelRequestBodies = otelRequests.map { it.bodyAsString }

            val testFailureMessage = "Assertion failed"
            expectThat(otelRequestBodies.find { it.contains(testFailureMessage) }).isNotNull()
        }
    }

    @Test
    fun `when plugin run in CI should include is-CI attribute`(wmRuntimeInfo: WireMockRuntimeInfo, @TempDir projectRootDirPath: Path) {
        val wiremockBaseUrl = wmRuntimeInfo.httpBaseUrl

        val buildFileContents = """
            ${baseBuildFileContents()}
            
            openTelemetryBuild {
                endpoint = '$wiremockBaseUrl/otel'
                exporterMode = com.atkinsondev.opentelemetry.build.OpenTelemetryExporterMode.HTTP
            }
        """.trimIndent()

        File(projectRootDirPath.toFile(), "build.gradle").writeText(buildFileContents)

        createSrcDirectoryAndClassFile(projectRootDirPath)
        createTestDirectoryAndClassFile(projectRootDirPath)

        stubFor(post("/otel").willReturn(ok()))

        val buildResult = GradleRunner.create()
            .withProjectDir(projectRootDirPath.toFile())
            .withArguments("test", "--info", "--stacktrace")
            .withEnvironment(mapOf("CI" to "true"))
            .withPluginClasspath()
            .build()

        expectThat(buildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        await().untilAsserted {
            val otelRequests = findAll(postRequestedFor(urlEqualTo("/otel")))

            val otelRequestBodies = otelRequests.map { it.bodyAsString }

            val ciSpanAttributeName = "system.is_ci"
            val ciAttributeBody = otelRequestBodies.find { it.contains(ciSpanAttributeName) }
            expectThat(ciAttributeBody).isNotNull()
        }
    }

    @Test
    fun `should include task names`(wmRuntimeInfo: WireMockRuntimeInfo, @TempDir projectRootDirPath: Path) {
        val wiremockBaseUrl = wmRuntimeInfo.httpBaseUrl

        val buildFileContents = """
            ${baseBuildFileContents()}
            
            openTelemetryBuild {
                endpoint = '$wiremockBaseUrl/otel'
                exporterMode = com.atkinsondev.opentelemetry.build.OpenTelemetryExporterMode.HTTP
            }
        """.trimIndent()

        File(projectRootDirPath.toFile(), "build.gradle").writeText(buildFileContents)

        createSrcDirectoryAndClassFile(projectRootDirPath)
        createTestDirectoryAndClassFile(projectRootDirPath)

        stubFor(post("/otel").willReturn(ok()))

        val buildResult = GradleRunner.create()
            .withProjectDir(projectRootDirPath.toFile())
            .withArguments("compileKotlin", "test", "--info")
            .withPluginClasspath()
            .build()

        expectThat(buildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        await().untilAsserted {
            val otelRequests = findAll(postRequestedFor(urlEqualTo("/otel")))

            val otelRequestBodies = otelRequests.map { it.bodyAsString }

            val taskNames = "compileKotlin test"
            expectThat(otelRequestBodies.find { it.contains(taskNames) }).isNotNull()
        }
    }

    @Test
    fun `should include task type`(wmRuntimeInfo: WireMockRuntimeInfo, @TempDir projectRootDirPath: Path) {
        val wiremockBaseUrl = wmRuntimeInfo.httpBaseUrl

        val buildFileContents = """
            ${baseBuildFileContents()}
            
            openTelemetryBuild {
                endpoint = '$wiremockBaseUrl/otel'
                exporterMode = com.atkinsondev.opentelemetry.build.OpenTelemetryExporterMode.HTTP
            }
        """.trimIndent()

        File(projectRootDirPath.toFile(), "build.gradle").writeText(buildFileContents)

        createSrcDirectoryAndClassFile(projectRootDirPath)
        createTestDirectoryAndClassFile(projectRootDirPath)

        stubFor(post("/otel").willReturn(ok()))

        val buildResult = GradleRunner.create()
            .withProjectDir(projectRootDirPath.toFile())
            .withArguments("compileKotlin", "test", "--info")
            .withPluginClasspath()
            .build()

        expectThat(buildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        await().untilAsserted {
            val otelRequests = findAll(postRequestedFor(urlEqualTo("/otel")))

            val otelRequestBodies = otelRequests.map { it.bodyAsString }

            val taskType = "org.gradle.api.tasks.testing.Test"
            expectThat(otelRequestBodies.find { it.contains(taskType) }).isNotNull()
        }
    }

    @Test
    fun `when plugin is applied but no endpoint defined should log message and disable plugin`(@TempDir projectRootDirPath: Path) {
        val buildFileContents = """
            ${baseBuildFileContents()}
        """.trimIndent()

        File(projectRootDirPath.toFile(), "build.gradle").writeText(buildFileContents)

        createSrcDirectoryAndClassFile(projectRootDirPath)
        createTestDirectoryAndClassFile(projectRootDirPath)

        val buildResult = GradleRunner.create()
            .withProjectDir(projectRootDirPath.toFile())
            .withArguments("test", "--info", "--stacktrace")
            .withPluginClasspath()
            .build()

        expectThat(buildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        expectThat(buildResult.output).contains("""No OpenTelemetry build endpoint found, disabling plugin. Please add "openTelemetryBuild { endpoint = '<server>' }" to your Gradle build file.""")
    }

    @Test
    fun `when plugin is disabled should log message`(wmRuntimeInfo: WireMockRuntimeInfo, @TempDir projectRootDirPath: Path) {
        val wiremockBaseUrl = wmRuntimeInfo.httpBaseUrl

        val buildFileContents = """
            ${baseBuildFileContents()}
            
            openTelemetryBuild {
                enabled = false
                endpoint = '$wiremockBaseUrl/otel'
                exporterMode = com.atkinsondev.opentelemetry.build.OpenTelemetryExporterMode.HTTP
            }
        """.trimIndent()

        File(projectRootDirPath.toFile(), "build.gradle").writeText(buildFileContents)

        createSrcDirectoryAndClassFile(projectRootDirPath)
        createTestDirectoryAndClassFile(projectRootDirPath)

        stubFor(post("/otel").willReturn(ok()))

        val buildResult = GradleRunner.create()
            .withProjectDir(projectRootDirPath.toFile())
            .withArguments("test", "--info", "--stacktrace")
            .withPluginClasspath()
            .build()

        expectThat(buildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        expectThat(buildResult.output).contains("""OpenTelemetry build plugin is disabled via configuration.""")

        expectThat(findAll(postRequestedFor(urlEqualTo("/otel")))).isEmpty()
    }
}
