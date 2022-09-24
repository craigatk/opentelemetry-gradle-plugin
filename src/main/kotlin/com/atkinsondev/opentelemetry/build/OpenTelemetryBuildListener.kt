package com.atkinsondev.opentelemetry.build

import io.opentelemetry.api.trace.Span
import io.opentelemetry.sdk.OpenTelemetrySdk
import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logger

class OpenTelemetryBuildListener(private val rootSpan: Span, private val openTelemetry: OpenTelemetrySdk, private val logger: Logger) : BuildListener {
    override fun settingsEvaluated(settings: Settings) {
        rootSpan.addEvent("settings.evaluated")
    }

    override fun projectsLoaded(gradle: Gradle) {
        rootSpan.addEvent("projects.loaded")
    }

    override fun projectsEvaluated(gradle: Gradle) {
        rootSpan.addEvent("projects.evaluated")
    }

    override fun buildFinished(buildResult: BuildResult) {
        rootSpan.setAttribute("build.success", buildResult.failure == null)

        rootSpan.end()

        logger.warn("\nOpenTelemetry build trace ID ${rootSpan.spanContext.traceId}")

        try {
            openTelemetry.sdkTracerProvider.forceFlush()
            openTelemetry.sdkTracerProvider.shutdown()
        } catch (e: Exception) {
            logger.warn("Error closing OpenTelemetry provider", e)
        }
    }
}
