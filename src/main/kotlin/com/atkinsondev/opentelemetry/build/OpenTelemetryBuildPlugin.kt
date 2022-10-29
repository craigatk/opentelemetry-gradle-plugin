package com.atkinsondev.opentelemetry.build

import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.gradleVersionKey
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.isCIKey
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.projectNameKey
import io.opentelemetry.api.baggage.Baggage
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

                    val headers: Map<String, String>? = try {
                        extension.headers.orNull ?: mapOf()
                    } catch (e: Exception) {
                        null
                    }

                    if (headers != null) {
                        val openTelemetry = OpenTelemetryInit(project.logger).init(
                            endpoint = endpoint,
                            headers = headers,
                            serviceName = serviceName,
                            exporterMode = extension.exporterMode.get()
                        )

                        val tracer = openTelemetry.getTracer(serviceName)

                        val taskNames = project.gradle.startParameter.taskNames

                        // Put the following attributes on all spans
                        val baggage = Baggage.builder()
                            .put(projectNameKey, project.name)
                            .put(gradleVersionKey, project.gradle.gradleVersion)
                            .put(isCIKey, isCI("CI").toString())
                            .build()

                        val rootSpanName = "${project.name}-build"
                        val rootSpan = tracer.spanBuilder(rootSpanName)
                            .setAttribute("build.task.names", taskNames.joinToString(" "))
                            .addBaggage(baggage)
                            .startSpan()

                        val buildListener = OpenTelemetryBuildListener(rootSpan, openTelemetry, project.logger)
                        project.gradle.addBuildListener(buildListener)

                        val taskListener = OpenTelemetryTaskListener(tracer, rootSpan, baggage, project.logger)
                        project.gradle.addListener(taskListener)
                    } else {
                        project.logger.warn(configErrorMessage)
                    }
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

        const val configErrorMessage = "Error reading config for OpenTelemetry build plugin - disabling plugin."

        fun isCI(ciEnvVariableName: String): Boolean = System.getenv(ciEnvVariableName) != null
    }
}
