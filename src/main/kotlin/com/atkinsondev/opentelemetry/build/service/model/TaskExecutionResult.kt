package com.atkinsondev.opentelemetry.build.service.model

import java.time.Instant

data class TaskExecutionResult(
    val name: String,
    val path: String,
    val startTime: Instant,
    val endTime: Instant,
    val failure: TaskFailureResult?,
    val outcome: String,
) {
    val key = this.path

    val failed = this.failure != null
}
