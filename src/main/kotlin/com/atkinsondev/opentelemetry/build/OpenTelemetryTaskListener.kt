package com.atkinsondev.opentelemetry.build

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.tasks.TaskState
import java.util.concurrent.ConcurrentHashMap

class OpenTelemetryTaskListener(private val tracer: Tracer) : TaskExecutionListener {
    private val taskSpanMap = ConcurrentHashMap<String, Span>()

    override fun beforeExecute(task: Task) {
        val taskKey = taskHashKey(task)

        val span = tracer.spanBuilder(taskKey).startSpan()

        taskSpanMap.put(taskKey, span)
    }

    override fun afterExecute(task: Task, taskState: TaskState) {
        val taskKey = taskHashKey(task)

        val span = taskSpanMap.get(taskKey)

        span?.end()
    }

    companion object {
        fun taskHashKey(task: Task) = task.project.path + ":" + task.path
    }
}
