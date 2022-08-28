package com.atkinsondev.opentelemetry.build

import org.gradle.api.Plugin
import org.gradle.api.Project

class OpenTelemetryBuildPlugin : Plugin<Project> {
    override fun apply(project: Project) {

        val extension = project.extensions.create("openTelemetryBuild", OpenTelemetryBuildPluginExtension::class.java)

        project.afterEvaluate {
            project.logger.info("Configuring OpenTelemetry build plugin")

            val serviceName = extension.serviceName.orNull ?: "gradle-builds"

            val openTelemetry = OpenTelemetryInit(project.logger).init(
                endpoint = extension.endpoint.orNull ?: throw IllegalArgumentException("OpenTelemetry endpoint is required in OpenTelemetry build plugin"),
                headers = extension.headers.orNull ?: mapOf(),
                serviceName = serviceName,
                exporterMode = extension.exporterMode.get()
            )

            val tracer = openTelemetry.getTracer(serviceName)

            val rootSpanName = "${project.name}-build"
            val rootSpan = tracer.spanBuilder(rootSpanName)
                .setAttribute("project.name", project.name)
                .setAttribute("gradle.version", project.gradle.gradleVersion)
                .startSpan()

            val buildListener = OpenTelemetryBuildListener(rootSpan, openTelemetry, project.logger)
            project.gradle.addBuildListener(buildListener)

            val taskListener = OpenTelemetryTaskListener(tracer, rootSpan, project.logger)
            project.gradle.addListener(taskListener)
        }
    }
}
