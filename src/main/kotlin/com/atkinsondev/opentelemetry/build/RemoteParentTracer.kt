package com.atkinsondev.opentelemetry.build

import io.opentelemetry.api.trace.*

object RemoteParentTracer {
    fun createdValidTraceId(traceId: String?): String? =
        if (traceId != null && TraceId.isValid(traceId)) TraceId.fromBytes(traceId.toByteArray()) else null

    fun createValidSpanId(spanId: String?): String? =
        if (spanId != null && SpanId.isValid(spanId)) SpanId.fromBytes(spanId.toByteArray()) else null

    fun createRemoteSpanContext(parentTraceIdHex: String, parentSpanIdHex: String): SpanContext =
        SpanContext.createFromRemoteParent(
            parentTraceIdHex,
            parentSpanIdHex,
            TraceFlags.getDefault(),
            TraceState.builder().build(),
        )
}
