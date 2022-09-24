package com.atkinsondev.opentelemetry.build

import org.gradle.api.Plugin
import org.gradle.api.Project

class OpenTelemetryBuildPlugin : Plugin<Project> {
    override fun apply(project: Project) {

        val extension = project.extensions.create("openTelemetryBuild", OpenTelemetryBuildPluginExtension::class.java)

        project.afterEvaluate {
            val enabled = extension.enabled.get()

            if (enabled) {
                project.logger.info("Configuring OpenTelemetry build plugin")

                val endpoint = extension.endpoint.orNull

                if (endpoint != null) {
                    val serviceName = extension.serviceName.orNull ?: "gradle-builds"

                    val openTelemetry = OpenTelemetryInit(project.logger).init(
                        endpoint = endpoint,
                        headers = extension.headers.orNull ?: mapOf(),
                        serviceName = serviceName,
                        exporterMode = extension.exporterMode.get()
                    )

                    val tracer = openTelemetry.getTracer(serviceName)

                    val taskNames = project.gradle.startParameter.taskNames

                    val rootSpanName = "${project.name}-build"
                    val rootSpan = tracer.spanBuilder(rootSpanName)
                        .setAttribute("project.name", project.name)
                        .setAttribute("gradle.version", project.gradle.gradleVersion)
                        .setAttribute("system.is_ci", isCI("CI"))
                        .setAttribute("build.task.names", taskNames.joinToString(" "))
                        .startSpan()

                    val buildListener = OpenTelemetryBuildListener(rootSpan, openTelemetry, project.logger)
                    project.gradle.addBuildListener(buildListener)

                    val taskListener = OpenTelemetryTaskListener(tracer, rootSpan, project.logger)
                    project.gradle.addListener(taskListener)
                } else {
                    project.logger.warn(missingEndpointMessage)
                }
            } else {
                project.logger.info(pluginNotEnabledMessage)
            }
        }
    }

    companion object {
        const val pluginNotEnabledMessage = "OpenTelemetry build plugin is disabled via configuration."

        const val missingEndpointMessage = """No OpenTelemetry build endpoint found, disabling plugin. Please add "openTelemetryBuild { endpoint = '<server>' }" to your Gradle build file."""

        fun isCI(ciEnvVariableName: String): Boolean = System.getenv(ciEnvVariableName) != null
    }
}
