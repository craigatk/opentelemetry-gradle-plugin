package com.atkinsondev.opentelemetry.build

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

        val span = tracer.spanBuilder(taskKey).setParent(Context.current().with(rootSpan)).startSpan()

        if (task is Test) {
            val testListener = OpenTelemetryTestListener(logger, span)
            task.addTestListener(testListener)
        }

        taskSpanMap.put(taskKey, span)
    }

    override fun afterExecute(task: Task, taskState: TaskState) {
        val taskKey = taskHashKey(task)

        val span = taskSpanMap.get(taskKey)

        span?.setAttribute("task.did_work", taskState.didWork)

        if (taskState is TaskStateInternal) {
            span?.setAttribute("task.outcome", taskState.outcome.toString())

            if (taskState.failure != null) {
                span?.setAttribute("task.failed", true)
                span?.setAttribute("task.failure", taskState.failure?.message ?: "")
            }
        }

        span?.end()
    }

    companion object {
        fun taskHashKey(task: Task): String = task.path
    }
}
