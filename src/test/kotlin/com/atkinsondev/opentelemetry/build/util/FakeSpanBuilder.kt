package com.atkinsondev.opentelemetry.build.util

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import java.util.concurrent.TimeUnit

class FakeSpanBuilder(
    private val span: Span,
) : SpanBuilder {
    override fun setParent(context: Context): SpanBuilder = this

    override fun setNoParent(): SpanBuilder = this

    override fun addLink(spanContext: SpanContext): SpanBuilder = this

    override fun addLink(
        spanContext: SpanContext,
        attributes: Attributes,
    ): SpanBuilder = this

    override fun setAttribute(
        key: String,
        value: String,
    ): SpanBuilder = this

    override fun setAttribute(
        key: String,
        value: Long,
    ): SpanBuilder = this

    override fun setAttribute(
        key: String,
        value: Double,
    ): SpanBuilder = this

    override fun setAttribute(
        key: String,
        value: Boolean,
    ): SpanBuilder = this

    override fun <T : Any?> setAttribute(
        key: AttributeKey<T>,
        value: T,
    ): SpanBuilder = this

    override fun setSpanKind(spanKind: SpanKind): SpanBuilder = this

    override fun setStartTimestamp(
        startTimestamp: Long,
        unit: TimeUnit,
    ): SpanBuilder = this

    override fun startSpan(): Span = span
}
