package com.atkinsondev.opentelemetry.build.service

import com.atkinsondev.opentelemetry.build.*
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.ERROR_KEY
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.ERROR_MESSAGE_KEY
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.FAILURE_MESSAGE_KEY
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.FAILURE_STACKTRACE_KEY
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.GRADLE_VERSION_KEY
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.IS_CI_KEY
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.PROJECT_NAME_KEY
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.TASK_FAILED_KEY
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.TASK_FAILURE_KEY
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.TASK_NAME_KEY
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.TASK_PATH_KEY
import com.atkinsondev.opentelemetry.build.service.model.TaskExecutionResult
import com.atkinsondev.opentelemetry.build.service.model.TestExecutionResult
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.OpenTelemetrySdk
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.time.Instant

abstract class TraceService : BuildService<TraceService.Params> {
    private val logger = Logging.getLogger(TraceService::class.java)

    interface Params : BuildServiceParameters {
        fun getTaskNames(): ListProperty<String>

        fun getProjectName(): Property<String>

        fun getGradleVersion(): Property<String>

        fun getIsCI(): Property<Boolean>

        fun getNestedTestSpans(): Property<Boolean>

        fun getEndpoint(): Property<String>

        fun getHeaders(): MapProperty<String, String>

        fun getServiceName(): Property<String>

        fun getExporterMode(): Property<OpenTelemetryExporterMode>

        fun getCustomTags(): MapProperty<String, String>

        fun getTraceViewUrl(): Property<String>

        fun getTraceViewType(): Property<TraceViewType>

        fun getBuildStartTimeMilli(): Property<Long>

        fun getParentSpanIdEnvVarName(): Property<String>

        fun getParentTraceIdEnvVarName(): Property<String>
    }

    private val openTelemetry: OpenTelemetrySdk by lazy {
        OpenTelemetryInit().init(
            endpoint = parameters.getEndpoint().get(),
            headers = parameters.getHeaders().get(),
            serviceName = parameters.getServiceName().get(),
            exporterMode = parameters.getExporterMode().get(),
            customTags = parameters.getCustomTags().get(),
        )
    }

    private val tracer: Tracer by lazy {
        openTelemetry.getTracer(parameters.getServiceName().get())
    }

    private val baggage: Baggage by lazy {
        Baggage.builder()
            .put(PROJECT_NAME_KEY, parameters.getProjectName().get())
            .put(GRADLE_VERSION_KEY, parameters.getGradleVersion().get())
            .put(IS_CI_KEY, parameters.getIsCI().get().toString())
            .build()
    }

    private lateinit var rootSpan: Span
    private var started: Boolean = false

    private val spanMap: MutableMap<String, Span> = mutableMapOf()

    fun start(): Pair<String, String> {
        val rootSpanName = "${parameters.getProjectName().get()}-build"

        val rootSpanBuilder =
            tracer.spanBuilder(rootSpanName)
                .setAttribute("build.task.names", parameters.getTaskNames().get().joinToString(" "))
                .addBaggage(baggage)
                .setStartTimestamp(Instant.ofEpochMilli(parameters.getBuildStartTimeMilli().get()))

        val parenSpanContext =
            ParentSpan.parentSpanContext(
                parentSpanIdEnvVarName = parameters.getParentSpanIdEnvVarName(),
                parentTraceIdEnvVarName = parameters.getParentTraceIdEnvVarName(),
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

        rootSpan = rootSpanBuilder.startSpan()

        logger.debug("Starting trace service [{}]", this)

        started = true

        return Pair(rootSpan.spanContext.traceId, rootSpan.spanContext.spanId)
    }

    fun createTaskSpan(executionResult: TaskExecutionResult): Span {
        if (!started) {
            start()
        }

        val spanBuilder =
            tracer
                .spanBuilder(executionResult.key)
                .setParent(Context.current().with(rootSpan))
                .addBaggage(baggage)
                .setStartTimestamp(executionResult.startTime)
                .setAttribute(TASK_NAME_KEY, executionResult.name)
                .setAttribute(TASK_PATH_KEY, executionResult.path)

        if (executionResult.failed) {
            spanBuilder.setAttribute(ERROR_KEY, true)
            spanBuilder.setAttribute(ERROR_MESSAGE_KEY, executionResult.failure?.failureMessage ?: "")
            spanBuilder.setAttribute(TASK_FAILED_KEY, true)
            spanBuilder.setAttribute(TASK_FAILURE_KEY, executionResult.failure?.failureMessage ?: "")
        }

        val span = spanBuilder.startSpan()

        span.end(executionResult.endTime)

        spanMap[executionResult.path] = span

        return span
    }

    fun createTestSpans(
        executionResults: List<TestExecutionResult>,
        testTaskSpan: Span,
    ): List<Span> {
        val testSpans: MutableMap<String, Span> = mutableMapOf()

        return executionResults.map { testSpans.getOrDefault(it.testName, createTestSpan(it, executionResults, testSpans, testTaskSpan)) }
    }

    private fun createTestSpan(
        executionResult: TestExecutionResult,
        executionResults: List<TestExecutionResult>,
        testSpans: MutableMap<String, Span>,
        testTaskSpan: Span,
    ): Span {
        if (testSpans.containsKey(executionResult.testName)) {
            return testSpans.get(executionResult.testName)!!
        }

        val parentSpan =
            if (executionResult.parentTestName == null) {
                testTaskSpan
            } else if (testSpans.containsKey(executionResult.parentTestName)) {
                testSpans.get(executionResult.parentTestName)
            } else {
                val parentExecution = executionResults.find { it.testName == executionResult.parentTestName }

                if (parentExecution != null) {
                    createTestSpan(parentExecution, executionResults, testSpans, testTaskSpan)
                } else {
                    testTaskSpan
                }
            }

        val spanBuilder =
            tracer
                .spanBuilder(executionResult.testDisplayName)
                .setParent(Context.current().with(parentSpan))
                .addBaggage(baggage)
                .setAttribute(TASK_NAME_KEY, executionResult.taskName)
                .setStartTimestamp(Instant.ofEpochMilli(executionResult.startTime))

        if (executionResult.failure != null) {
            spanBuilder.setAttribute(ERROR_KEY, true)
            spanBuilder.setAttribute(ERROR_MESSAGE_KEY, executionResult.failure.message ?: "")
            spanBuilder.setAttribute(FAILURE_MESSAGE_KEY, executionResult.failure.message ?: "")
            spanBuilder.setAttribute(FAILURE_STACKTRACE_KEY, executionResult.failure.stackTrace ?: "")
        }

        val span = spanBuilder.startSpan()

        span.end(Instant.ofEpochMilli(executionResult.endTime))

        testSpans.put(executionResult.testName, span)

        return span
    }

    fun close(buildFailed: Boolean) {
        logger.debug("Closing trace service [{}]", this)

        rootSpan.setAttribute("build.success", !buildFailed)

        rootSpan.end()

        val traceLogger = TraceLogger(parameters.getTraceViewUrl().orNull, parameters.getTraceViewType().orNull)

        traceLogger.logTrace(rootSpan.spanContext.traceId)

        try {
            openTelemetry.sdkTracerProvider.forceFlush()
            openTelemetry.sdkTracerProvider.shutdown()
        } catch (e: Exception) {
            logger.warn("Error closing OpenTelemetry provider", e)
        }
    }
}
