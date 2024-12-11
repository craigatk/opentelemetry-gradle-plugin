package com.atkinsondev.opentelemetry.build

import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.GRADLE_VERSION_KEY
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.IS_CI_KEY
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.PROJECT_NAME_KEY
import com.atkinsondev.opentelemetry.build.service.*
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.TaskCollection
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.util.GradleVersion
import java.time.Instant
import javax.inject.Inject

abstract class OpenTelemetryBuildPlugin : Plugin<Project> {
    @Inject
    abstract fun getEventsListenerRegistry(): BuildEventsListenerRegistry

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
                        // org.gradle.api.configuration.BuildFeatures class was added in Gradle 8.5
                        // otherwise fall back to the plugin extension parameter
                        val configurationCacheRequested =
                            if (GradleVersion.current() >= GradleVersion.version("8.5")) {
                                val configCacheService = project.gradle.sharedServices.registerIfAbsent("configCache", ConfigCacheService::class.java) { spec -> }

                                extension.supportConfigCache.getOrElse(false) || configCacheService.get().configCacheRequested()
                            } else {
                                extension.supportConfigCache.getOrElse(false)
                            }

                        if (configurationCacheRequested) {
                            project.logger.info("Using configuration-cache compatible task events service")

                            val traceServiceProvider =
                                project.gradle.sharedServices.registerIfAbsent("trace", TraceService::class.java) { spec ->
                                    spec.parameters.getTaskNames().set(project.gradle.startParameter.taskNames)
                                    spec.parameters.getProjectName().set(project.name)
                                    spec.parameters.getGradleVersion().set(project.gradle.gradleVersion)
                                    spec.parameters.getIsCI().set(isCI("CI"))
                                    spec.parameters.getNestedTestSpans().set(extension.nestedTestSpans.get())

                                    spec.parameters.getEndpoint().set(endpoint)
                                    spec.parameters.getHeaders().set(headers)
                                    spec.parameters.getServiceName().set(serviceName)
                                    spec.parameters.getExporterMode().set(extension.exporterMode.get())
                                    spec.parameters.getCustomTags().set(customTags.orEmpty())

                                    spec.parameters.getTraceViewUrl().set(extension.traceViewUrl)
                                    spec.parameters.getTraceViewType().set(extension.traceViewType)

                                    spec.parameters.getBuildStartTimeMilli().set(Instant.now().toEpochMilli())

                                    spec.parameters.getParentSpanIdEnvVarName().set(extension.parentSpanIdEnvVarName)
                                    spec.parameters.getParentTraceIdEnvVarName().set(extension.parentTraceIdEnvVarName)
                                }

                            val testExecutionTrackerServiceProvider = project.gradle.sharedServices.registerIfAbsent("testTracker", TestExecutionTrackerService::class.java) { spec -> }

                            val taskEventsServiceProvider =
                                project.gradle.sharedServices.registerIfAbsent("taskEvents", TaskEventsService::class.java) { spec ->
                                    spec.parameters.getTraceService().set(traceServiceProvider)
                                }
                            getEventsListenerRegistry().onTaskCompletion(taskEventsServiceProvider)

                            val testTasks: TaskCollection<org.gradle.api.tasks.testing.Test> = project.tasks.withType(org.gradle.api.tasks.testing.Test::class.java)
                            testTasks.forEach { testTask ->
                                val testListener =
                                    TestListenerService(
                                        testTaskPath = testTask.path,
                                        testTaskName = testTask.name,
                                        nestedTestSpans = extension.nestedTestSpans.getOrElse(false),
                                        testExecutionTrackerService = testExecutionTrackerServiceProvider,
                                    )
                                testTask.usesService(testExecutionTrackerServiceProvider)

                                testTask.addTestListener(testListener)
                            }

                            if (extension.taskTraceEnvironmentEnabled.getOrElse(false)) {
                                val traceSpanPair = traceServiceProvider.get().start()

                                passTraceContextToExecTasks(
                                    traceId = traceSpanPair.first,
                                    spanId = traceSpanPair.second,
                                    extension,
                                    project,
                                )
                            }
                        } else {
                            val taskNames = project.gradle.startParameter.taskNames

                            val openTelemetry =
                                OpenTelemetryInit().init(
                                    endpoint = endpoint,
                                    headers = headers,
                                    serviceName = serviceName,
                                    exporterMode = extension.exporterMode.get(),
                                    customTags = customTags.orEmpty(),
                                )

                            val tracer = openTelemetry.getTracer(serviceName)

                            // Put the following attributes on all spans
                            val baggage =
                                Baggage.builder()
                                    .put(PROJECT_NAME_KEY, project.name)
                                    .put(GRADLE_VERSION_KEY, project.gradle.gradleVersion)
                                    .put(IS_CI_KEY, isCI("CI").toString())
                                    .build()

                            val rootSpanName = "${project.name}-build"

                            val rootSpanBuilder =
                                tracer.spanBuilder(rootSpanName)
                                    .setAttribute("build.task.names", taskNames.joinToString(" "))
                                    .addBaggage(baggage)

                            val parenSpanContext =
                                ParentSpan.parentSpanContext(
                                    parentSpanIdEnvVarName = extension.parentSpanIdEnvVarName,
                                    parentTraceIdEnvVarName = extension.parentTraceIdEnvVarName,
                                    SystemEnvironmentSource(),
                                )
                            val parentContext =
                                if (parenSpanContext != null) {
                                    Context.root().with(Span.wrap(parenSpanContext))
                                } else {
                                    null
                                }
                            if (parentContext != null) {
                                rootSpanBuilder.setParent(parentContext)
                            }

                            val rootSpan = rootSpanBuilder.startSpan()

                            if (extension.taskTraceEnvironmentEnabled.getOrElse(false)) {
                                passTraceContextToExecTasks(
                                    traceId = rootSpan.spanContext.traceId,
                                    spanId = rootSpan.spanContext.spanId,
                                    extension,
                                    project,
                                )
                            }

                            val traceLogger = TraceLogger(extension.traceViewUrl.orNull, extension.traceViewType.orNull, project.logger)

                            val buildListener = OpenTelemetryBuildListener(rootSpan, openTelemetry, traceLogger, project.logger)
                            project.gradle.addBuildListener(buildListener)

                            val taskListener =
                                OpenTelemetryTaskListener(
                                    tracer = tracer,
                                    rootSpan = rootSpan,
                                    baggage = baggage,
                                    logger = project.logger,
                                    nestedTestSpans = extension.nestedTestSpans.get(),
                                )
                            project.gradle.addListener(taskListener)
                        }
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

    private fun passTraceContextToExecTasks(
        traceId: String,
        spanId: String,
        extension: OpenTelemetryBuildPluginExtension,
        project: Project,
    ) {
        val execTasks: TaskCollection<Exec> = project.tasks.withType(Exec::class.java)

        execTasks.forEach { task ->
            task.environment[extension.taskTraceEnvironmentTraceIdName.get()] = traceId
            task.environment[extension.taskTraceEnvironmentSpanIdName.get()] = spanId
        }
    }

    companion object {
        const val PLUGIN_NOT_ENABLED_MESSAGE = "OpenTelemetry build plugin is disabled via configuration."

        const val MISSING_ENDPOINT_MESSAGE = """No OpenTelemetry build endpoint found, disabling plugin. Please add "openTelemetryBuild { endpoint = '<server>' }" to your Gradle build file."""

        const val CONFIG_ERROR_MESSAGE = "Error reading config for OpenTelemetry build plugin - disabling plugin."

        fun isCI(ciEnvVariableName: String): Boolean = System.getenv(ciEnvVariableName) != null
    }
}
