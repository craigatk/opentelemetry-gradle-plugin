package com.atkinsondev.opentelemetry.build

import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.errorKey
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.projectNameKey
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.taskDidWorkKey
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.taskFailedKey
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.taskFailureKey
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.taskNameKey
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.taskOutcomeKey
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.taskPathKey
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.taskTypeKey
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
    private val logger: Logger
) : TaskExecutionListener {
    private val taskSpanMap = ConcurrentHashMap<String, Span>()

    override fun beforeExecute(task: Task) {
        val taskKey = taskHashKey(task)

        val span = tracer
            .spanBuilder(taskKey)
            .setParent(Context.current().with(rootSpan))
            .startSpan()
            .setAttribute(projectNameKey, task.project.name)
            .setAttribute(taskNameKey, task.name)
            .setAttribute(taskPathKey, task.path)
            .setAttribute(taskTypeKey, task.javaClass.name.replace("_Decorated", ""))

        if (task is Test) {
            val testListener = OpenTelemetryTestListener(
                tracer = tracer,
                testTaskSpan = span,
                projectName = task.project.name,
                testTaskName = task.name,
                logger = logger,
            )
            task.addTestListener(testListener)
        }

        taskSpanMap.put(taskKey, span)
    }

    override fun afterExecute(task: Task, taskState: TaskState) {
        val taskKey = taskHashKey(task)

        val span = taskSpanMap.get(taskKey)

        span?.setAttribute(taskDidWorkKey, taskState.didWork)

        if (taskState is TaskStateInternal) {
            span?.setAttribute(taskOutcomeKey, taskState.outcome.toString())

            if (taskState.failure != null) {
                span?.setAttribute(errorKey, taskState.failure?.message ?: "")
                span?.setAttribute(taskFailedKey, true)
                span?.setAttribute(taskFailureKey, taskState.failure?.message ?: "")
            }
        }

        span?.end()
    }

    companion object {
        fun taskHashKey(task: Task): String = task.path
    }
}
