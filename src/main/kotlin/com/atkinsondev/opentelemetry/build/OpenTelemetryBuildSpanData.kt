package com.atkinsondev.opentelemetry.build

object OpenTelemetryBuildSpanData {
    // Project attributes
    const val projectNameKey = "project.name"

    // Task attributes
    const val taskNameKey = "task.name"
    const val taskPathKey = "task.path"
    const val taskDidWorkKey = "task.did_work"
    const val taskOutcomeKey = "task.outcome"
    const val errorKey = "error"
    const val taskFailedKey = "task.failed"
    const val taskFailureKey = "task.failure"

    // Test attributes
    const val testNameKey = "test.name"
    const val testResultKey = "test.result"
    const val failureMessageKey = "test.failure.message"
    const val failureStacktraceKey = "test.failure.stacktrace"

    const val testFailureSpanEventName = "test.failure"
}
