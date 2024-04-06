package com.atkinsondev.opentelemetry.build.util

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import java.util.concurrent.TimeUnit

class FakeSpanBuilder(private val span: Span) : SpanBuilder {
    override fun setParent(context: Context): SpanBuilder {
        return this
    }

    override fun setNoParent(): SpanBuilder {
        return this
    }

    override fun addLink(spanContext: SpanContext): SpanBuilder {
        return this
    }

    override fun addLink(
        spanContext: SpanContext,
        attributes: Attributes,
    ): SpanBuilder {
        return this
    }

    override fun setAttribute(
        key: String,
        value: String,
    ): SpanBuilder {
        return this
    }

    override fun setAttribute(
        key: String,
        value: Long,
    ): SpanBuilder {
        return this
    }

    override fun setAttribute(
        key: String,
        value: Double,
    ): SpanBuilder {
        return this
    }

    override fun setAttribute(
        key: String,
        value: Boolean,
    ): SpanBuilder {
        return this
    }

    override fun <T : Any?> setAttribute(
        key: AttributeKey<T>,
        value: T,
    ): SpanBuilder {
        return this
    }

    override fun setSpanKind(spanKind: SpanKind): SpanBuilder {
        return this
    }

    override fun setStartTimestamp(
        startTimestamp: Long,
        unit: TimeUnit,
    ): SpanBuilder {
        return this
    }

    override fun startSpan(): Span {
        return span
    }
}
