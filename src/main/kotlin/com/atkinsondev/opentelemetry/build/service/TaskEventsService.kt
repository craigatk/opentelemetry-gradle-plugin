package com.atkinsondev.opentelemetry.build.service

import com.atkinsondev.opentelemetry.build.service.model.TaskExecutionResult
import com.atkinsondev.opentelemetry.build.service.model.TaskFailureResult
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.ServiceReference
import org.gradle.tooling.events.FailureResult
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFinishEvent
import java.time.Instant

abstract class TaskEventsService : BuildService<TaskEventsService.Params>, OperationCompletionListener, AutoCloseable {
    interface Params : BuildServiceParameters {
        @ServiceReference("trace")
        fun getTraceService(): Property<TraceService>

        @ServiceReference("testTracker")
        fun getTestExecutionTrackerService(): Property<TestExecutionTrackerService>
    }

    private var buildFailed: Boolean = false

    override fun onFinish(finishEvent: FinishEvent) {
        if (finishEvent is TaskFinishEvent) {
            val taskFailureResult =
                if (finishEvent.result is FailureResult) {
                    buildFailed = true

                    val failureResult = finishEvent.result as FailureResult
                    failureResult.failures

                    val failureMessage = failureResult.failures.firstOrNull()?.message ?: ""

                    TaskFailureResult(
                        failureMessage = failureMessage,
                    )
                } else {
                    null
                }

            val taskExecutionResult =
                TaskExecutionResult(
                    path = finishEvent.descriptor.taskPath,
                    name = finishEvent.descriptor.name,
                    startTime = Instant.ofEpochMilli(finishEvent.result.startTime),
                    endTime = Instant.ofEpochMilli(finishEvent.result.endTime),
                    failure = taskFailureResult,
                )

            val taskSpan = parameters.getTraceService().get().createTaskSpan(taskExecutionResult)

            val testExecutions = parameters.getTestExecutionTrackerService().get().getTestExecutionsForTask(finishEvent.descriptor.taskPath)

            if (testExecutions.isNotEmpty()) {
                parameters.getTraceService().get().createTestSpans(testExecutions, taskSpan)
            }
        }
    }

    override fun close() {
        parameters.getTraceService().get().close(buildFailed)
    }
}
