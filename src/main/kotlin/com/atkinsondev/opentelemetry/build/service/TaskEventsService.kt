package com.atkinsondev.opentelemetry.build.service

import com.atkinsondev.opentelemetry.build.service.model.TaskExecutionResult
import com.atkinsondev.opentelemetry.build.service.model.TaskFailureResult
import org.gradle.api.internal.provider.MissingValueException
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.ServiceReference
import org.gradle.tooling.events.FailureResult
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSkippedResult
import org.gradle.tooling.events.task.TaskSuccessResult
import java.time.Instant
import org.gradle.tooling.events.task.TaskExecutionResult as GradleTaskExecutionResult

abstract class TaskEventsService : BuildService<TaskEventsService.Params>, OperationCompletionListener, AutoCloseable {
    private val logger: Logger = Logging.getLogger(TaskEventsService::class.java)

    interface Params : BuildServiceParameters {
        @ServiceReference("trace")
        fun getTraceService(): Property<TraceService>

        @ServiceReference("testTracker")
        fun getTestExecutionTrackerService(): Property<TestExecutionTrackerService>
    }

    private var buildFailed: Boolean = false

    override fun onFinish(finishEvent: FinishEvent) {
        if (finishEvent is TaskFinishEvent) {
            val result = finishEvent.result

            val outcome =
                when (result) {
                    // TaskSkippedResult.skipMessage can either be NO-SOURCE or SKIPPED
                    is TaskSkippedResult -> result.skipMessage
                    is FailureResult -> "EXECUTED"
                    is TaskSuccessResult -> {
                        when {
                            result.isFromCache -> "FROM-CACHE"
                            result.isUpToDate -> "UP-TO-DATE"
                            else -> "EXECUTED"
                        }
                    }

                    else -> ""
                }

            val (isIncremental, executionReasons) =
                if (result is GradleTaskExecutionResult) {
                    result.isIncremental to result.executionReasons
                } else {
                    null to null
                }

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
                    outcome = outcome,
                    executionReasons = executionReasons ?: emptyList(),
                    isIncremental = isIncremental,
                )

            val taskSpan = parameters.getTraceService().get().createTaskSpan(taskExecutionResult)

            try {
                val testExecutions = parameters.getTestExecutionTrackerService().get().getTestExecutionsForTask(finishEvent.descriptor.taskPath)

                if (testExecutions.isNotEmpty()) {
                    parameters.getTraceService().get().createTestSpans(testExecutions, taskSpan)
                }
            } catch (_: MissingValueException) {
                // When the build isn't running tests, the TestExecutionTrackerService won't get instantiated
                logger.debug("Received missing value exception")
            }
        }
    }

    override fun close() {
        parameters.getTraceService().get().close(buildFailed)
    }
}
