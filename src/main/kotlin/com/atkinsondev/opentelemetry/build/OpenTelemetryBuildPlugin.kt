package com.atkinsondev.opentelemetry.build

import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.GRADLE_VERSION_KEY
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.IS_CI_KEY
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.PROJECT_NAME_KEY
import com.atkinsondev.opentelemetry.build.RemoteParentTracer.createRemoteSpanContext
import com.atkinsondev.opentelemetry.build.RemoteParentTracer.createValidSpanId
import com.atkinsondev.opentelemetry.build.RemoteParentTracer.createdValidTraceId
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.trace.*
import io.opentelemetry.context.Context
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger

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

                    val headers: Map<String, String>? =
                        try {
                            extension.headers.orNull ?: mapOf()
                        } catch (e: Exception) {
                            null
                        }

                    val customTags: Map<String, String>? =
                        try {
                            extension.customTags.orNull ?: mapOf()
                        } catch (e: Exception) {
                            null
                        }

                    if (headers != null) {
                        val openTelemetry =
                            OpenTelemetryInit(project.logger).init(
                                endpoint = endpoint,
                                headers = headers,
                                serviceName = serviceName,
                                exporterMode = extension.exporterMode.get(),
                                customTags = customTags.orEmpty(),
                            )

                        val tracer = openTelemetry.getTracer(serviceName)

                        val taskNames = project.gradle.startParameter.taskNames

                        // Put the following attributes on all spans
                        val baggage =
                            Baggage.builder()
                                .put(PROJECT_NAME_KEY, project.name)
                                .put(GRADLE_VERSION_KEY, project.gradle.gradleVersion)
                                .put(IS_CI_KEY, isCI("CI").toString())
                                .build()

                        val rootSpanName = "${project.name}-build"

                        val parenSpanContext = parentSpanContext(extension, SystemEnvironmentSource(), project.logger)

                        val parentContext =
                            if (parenSpanContext != null) {
                                Context.root().with(Span.wrap(parenSpanContext))
                            } else {
                                null
                            }

                        val rootSpanBuilder =
                            tracer.spanBuilder(rootSpanName)
                                .setAttribute("build.task.names", taskNames.joinToString(" "))
                                .addBaggage(baggage)

                        if (parentContext != null) {
                            rootSpanBuilder.setParent(parentContext)
                        }

                        val rootSpan = rootSpanBuilder.startSpan()

                        val buildListener = OpenTelemetryBuildListener(rootSpan, openTelemetry, project.logger)
                        project.gradle.addBuildListener(buildListener)

                        val taskListener = OpenTelemetryTaskListener(tracer, rootSpan, baggage, project.logger)
                        project.gradle.addListener(taskListener)
                    } else {
                        project.logger.warn(CONFIG_ERROR_MESSAGE)
                    }
                } else {
                    project.logger.warn(MISSING_ENDPOINT_MESSAGE)
                }
            } else {
                project.logger.info(PLUGIN_NOT_ENABLED_MESSAGE)
            }
        }
    }

    companion object {
        const val PLUGIN_NOT_ENABLED_MESSAGE = "OpenTelemetry build plugin is disabled via configuration."

        const val MISSING_ENDPOINT_MESSAGE = """No OpenTelemetry build endpoint found, disabling plugin. Please add "openTelemetryBuild { endpoint = '<server>' }" to your Gradle build file."""

        const val CONFIG_ERROR_MESSAGE = "Error reading config for OpenTelemetry build plugin - disabling plugin."

        fun isCI(ciEnvVariableName: String): Boolean = System.getenv(ciEnvVariableName) != null

        fun parentSpanContext(
            extension: OpenTelemetryBuildPluginExtension,
            environmentSource: EnvironmentSource,
            logger: Logger,
        ): SpanContext? {
            // Create a parent context passed in from a CI system like Jenkins to tie the Gradle trace
            // with one created by a parent system.
            // Ref https://github.com/open-telemetry/opentelemetry-java/discussions/4668
            if (extension.parentSpanIdEnvVarName.isPresent && extension.parentTraceIdEnvVarName.isPresent) {
                logger.info(
                    "Reading parent span ID from environment variable {} and trace ID from environment variable {}",
                    extension.parentSpanIdEnvVarName.get(),
                    extension.parentTraceIdEnvVarName.get(),
                )

                val parentSpanIdStr = environmentSource.getenv(extension.parentSpanIdEnvVarName.get())
                val parentTraceIdStr = environmentSource.getenv(extension.parentTraceIdEnvVarName.get())

                val parentSpanIdHex = createValidSpanId(parentSpanIdStr)
                val parentTraceIdHex = createdValidTraceId(parentTraceIdStr)

                if (parentSpanIdHex != null && parentTraceIdHex != null) {
                    val remoteSpanContext = createRemoteSpanContext(parentTraceIdHex, parentSpanIdHex)

                    if (remoteSpanContext.isValid) {
                        logger.info("Using parent span ID {} and parent trace ID {}", parentSpanIdStr, parentTraceIdStr)

                        return remoteSpanContext
                    } else {
                        logger.warn("Remote span context is not valid")
                    }
                } else {
                    if (parentSpanIdHex == null) {
                        logger.warn("Received invalid parent span ID {}", parentSpanIdStr)
                    }

                    if (parentTraceIdHex == null) {
                        logger.warn("Received invalid parent trace ID {}", parentTraceIdStr)
                    }
                }
            }

            return null
        }
    }
}
