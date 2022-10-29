package com.atkinsondev.opentelemetry.build

import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.trace.SpanBuilder

fun SpanBuilder.addBaggage(baggage: Baggage): SpanBuilder {
    baggage.forEach { key, value ->
        this.setAttribute(key, value.value)
    }

    return this
}
