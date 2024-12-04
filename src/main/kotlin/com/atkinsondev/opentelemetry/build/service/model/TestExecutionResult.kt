package com.atkinsondev.opentelemetry.build.service.model

data class TestExecutionResult(
    val testName: String,
    val testDisplayName: String,
    val taskPath: String,
    val taskName: String,
    val startTime: Long,
    val endTime: Long,
    val failure: TestExecutionFailure?,
    val parentTestName: String?,
)
