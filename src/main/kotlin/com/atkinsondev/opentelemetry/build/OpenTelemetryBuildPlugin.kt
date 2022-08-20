package com.atkinsondev.opentelemetry.build

import org.gradle.api.Plugin
import org.gradle.api.Project

class OpenTelemetryBuildPlugin : Plugin<Project> {
    override fun apply(project: Project) {

        val extension = project.extensions.create("openTelemetryBuild", OpenTelemetryBuildPluginExtension::class.java)

        project.afterEvaluate {
            val serviceName = "${project.name}-build"
            val endpoint = "http://localhost:8080"
            val headers: Map<String, String> = mapOf()

            val openTelemetry = OpenTelemetryInit().init(
                endpoint = endpoint,
                headers = headers,
                serviceName = serviceName,
            )

            val tracer = openTelemetry.getTracer(serviceName)

            val rootSpan = tracer.spanBuilder("build").startSpan()

            val buildListener = OpenTelemetryBuildListener(rootSpan)

            project.gradle.addBuildListener(buildListener)
        }
    }
}
