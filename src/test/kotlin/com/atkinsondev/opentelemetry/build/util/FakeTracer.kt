package com.atkinsondev.opentelemetry.build.util

import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer

class FakeTracer(private val spanBuilder: SpanBuilder) : Tracer {
    override fun spanBuilder(spanName: String): SpanBuilder {
        return spanBuilder
    }
}
