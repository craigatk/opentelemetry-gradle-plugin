package com.atkinsondev.opentelemetry.build

import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.ERROR_KEY
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.ERROR_MESSAGE_KEY
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.TASK_DID_WORK_KEY
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.TASK_FAILED_KEY
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.TASK_FAILURE_KEY
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.TASK_NAME_KEY
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.TASK_OUTCOME_KEY
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.TASK_PATH_KEY
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.TASK_TYPE_KEY
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.TaskState
import org.gradle.api.tasks.testing.Test
import java.util.concurrent.ConcurrentHashMap

class OpenTelemetryTaskListener(
    private val tracer: Tracer,
    private val rootSpan: Span,
    private val baggage: Baggage,
    private val logger: Logger,
    private val nestedTestSpans: Boolean,
) : TaskExecutionListener {
    private val taskSpanMap = ConcurrentHashMap<String, Span>()

    override fun beforeExecute(task: Task) {
        val taskKey = taskHashKey(task)

        val span =
            tracer
                .spanBuilder(taskKey)
                .setParent(Context.current().with(rootSpan))
                .addBaggage(baggage)
                .startSpan()
                .setAttribute(TASK_NAME_KEY, task.name)
                .setAttribute(TASK_PATH_KEY, task.path)
                .setAttribute(TASK_TYPE_KEY, task.javaClass.name.replace("_Decorated", ""))

        if (task is Test) {
            val testListener =
                OpenTelemetryTestListener(
                    tracer = tracer,
                    testTaskSpan = span,
                    baggage = baggage,
                    testTaskName = task.name,
                    logger = logger,
                    nestedTestSpans = nestedTestSpans,
                )
            task.addTestListener(testListener)
        }

        taskSpanMap[taskKey] = span
    }

    override fun afterExecute(
        task: Task,
        taskState: TaskState,
    ) {
        val taskKey = taskHashKey(task)

        val span = taskSpanMap[taskKey]

        span?.setAttribute(TASK_DID_WORK_KEY, taskState.didWork)

        if (taskState is TaskStateInternal) {
            span?.setAttribute(TASK_OUTCOME_KEY, taskState.outcome.toString())

            if (taskState.failure != null) {
                span?.setAttribute(ERROR_KEY, true)
                span?.setAttribute(ERROR_MESSAGE_KEY, taskState.failure?.message ?: "")
                span?.setAttribute(TASK_FAILED_KEY, true)
                span?.setAttribute(TASK_FAILURE_KEY, taskState.failure?.message ?: "")
            }
        }

        span?.end()
    }

    companion object {
        fun taskHashKey(task: Task): String = task.path
    }
}
