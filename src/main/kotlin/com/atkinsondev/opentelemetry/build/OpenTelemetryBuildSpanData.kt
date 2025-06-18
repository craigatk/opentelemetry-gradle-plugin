package com.atkinsondev.opentelemetry.build

object OpenTelemetryBuildSpanData {
    // Attributes on all spans
    const val PROJECT_NAME_KEY = "project.name"
    const val GRADLE_VERSION_KEY = "gradle.version"
    const val IS_CI_KEY = "system.is_ci"

    // Task attributes
    const val TASK_NAME_KEY = "task.name"
    const val TASK_PATH_KEY = "task.path"
    const val TASK_TYPE_KEY = "task.type"
    const val TASK_DID_WORK_KEY = "task.did_work"
    const val TASK_OUTCOME_KEY = "task.outcome"
    const val TASK_EXECUTION_REASONS_KEY = "task.execution_reasons"
    const val TASK_IS_INCREMENTAL_KEY = "task.is_incremental"
    const val ERROR_KEY = "error"
    const val ERROR_MESSAGE_KEY = "error_message"
    const val TASK_FAILED_KEY = "task.failed"
    const val TASK_FAILURE_KEY = "task.failure"

    // Test attributes
    const val TEST_NAME_KEY = "test.name"
    const val TEST_RESULT_KEY = "test.result"
    const val FAILURE_MESSAGE_KEY = "test.failure.message"
    const val FAILURE_STACKTRACE_KEY = "test.failure.stacktrace"

    const val TEST_FAILURE_SPAN_EVENT_NAME_KEY = "test.failure"
}
