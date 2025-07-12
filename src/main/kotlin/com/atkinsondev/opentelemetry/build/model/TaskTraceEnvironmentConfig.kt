package com.atkinsondev.opentelemetry.build.model

data class TaskTraceEnvironmentConfig(
    val enabled: Boolean,
    val traceIdName: String = "TRACE_ID",
    val spanIdName: String = "SPAN_ID",
    val traceParentName: String = "TRACEPARENT",
)
