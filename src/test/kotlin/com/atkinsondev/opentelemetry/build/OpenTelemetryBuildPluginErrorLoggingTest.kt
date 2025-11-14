package com.atkinsondev.opentelemetry.build

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import strikt.api.expectThat
import strikt.assertions.contains
import java.io.File
import java.nio.file.Path

class OpenTelemetryBuildPluginErrorLoggingTest {
    @Test
    fun `when sending to OpenTelemetry fails should not log export-failed message`(
        @TempDir projectRootDirPath: Path,
    ) {
        val buildFileContents =
            """
            ${baseBuildFileContents()}
            
            openTelemetryBuild {
                endpoint = 'http://localhost:54321/otel'
            }
            """.trimIndent()

        File(projectRootDirPath.toFile(), "build.gradle").writeText(buildFileContents)

        createSrcDirectoryAndClassFile(projectRootDirPath)
        createTestDirectoryAndClassFile(projectRootDirPath)

        val buildResult =
            GradleRunner
                .create()
                .withProjectDir(projectRootDirPath.toFile())
                .withArguments("test")
                .withPluginClasspath()
                .build()

        println(buildResult.output)

        expectThat(buildResult.output).not().contains("Failed to export spans")
    }
}
