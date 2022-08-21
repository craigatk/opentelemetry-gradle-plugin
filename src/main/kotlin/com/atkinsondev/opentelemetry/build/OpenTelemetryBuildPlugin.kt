package com.atkinsondev.opentelemetry.build

import org.gradle.api.Plugin
import org.gradle.api.Project

class OpenTelemetryBuildPlugin : Plugin<Project> {
    override fun apply(project: Project) {

        val extension = project.extensions.create("openTelemetryBuild", OpenTelemetryBuildPluginExtension::class.java)

        project.afterEvaluate {
            val serviceName = "${project.name}-build"

            val openTelemetry = OpenTelemetryInit().init(
                endpoint = extension.endpoint.get(),
                headers = extension.headers.get(),
                serviceName = serviceName,
                exporterMode = extension.exporterMode.get()
            )

            val tracer = openTelemetry.getTracer(serviceName)

            val rootSpan = tracer.spanBuilder("build").startSpan()

            val buildListener = OpenTelemetryBuildListener(rootSpan)

            project.gradle.addBuildListener(buildListener)
        }
    }
}
