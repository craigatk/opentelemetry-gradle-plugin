package com.atkinsondev.opentelemetry.build

import io.opentelemetry.api.trace.Span
import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle

class OpenTelemetryBuildListener(private val rootSpan: Span) : BuildListener {
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
        rootSpan.setAttribute("success", buildResult.failure == null)

        rootSpan.end()
    }
}