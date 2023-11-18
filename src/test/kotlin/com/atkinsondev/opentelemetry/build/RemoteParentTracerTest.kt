package com.atkinsondev.opentelemetry.build

import com.atkinsondev.opentelemetry.build.RemoteParentTracer.createValidSpanId
import com.atkinsondev.opentelemetry.build.RemoteParentTracer.createdValidTraceId
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull

class RemoteParentTracerTest {
    @Test
    fun whenSpanIdValidShouldCreateHexValue() {
        val spanId = "f1a2153e247b0d94"

        val spanIdHex = createValidSpanId(spanId)

        expectThat(spanIdHex).isNotNull().isEqualTo("f1a2153e247b0d94")
    }

    @Test
    fun whenSpanIdInvalidShouldReturnNull() {
        val spanId = "invalid"

        val spanIdHex = createValidSpanId(spanId)

        expectThat(spanIdHex).isNull()
    }

    @Test
    fun whenSpanIdIsNullShouldReturnNull() {
        val spanId = null

        val spanIdHex = createValidSpanId(spanId)

        expectThat(spanIdHex).isNull()
    }

    @Test
    fun whenTraceIdIsValidShouldCreateHexValue() {
        val traceId = "a263fdf001993a32980b9ec5740b7d6d"

        val traceIdHex = createdValidTraceId(traceId)

        expectThat(traceIdHex).isNotNull().isEqualTo("a263fdf001993a32980b9ec5740b7d6d")
    }

    @Test
    fun whenTraceIdIsInvalidShouldReturnNull() {
        val traceId = "invalid"

        val traceIdHex = createdValidTraceId(traceId)

        expectThat(traceIdHex).isNull()
    }

    @Test
    fun whenTraceIdIsNullShouldReturnNull() {
        val traceId = null

        val traceIdHex = createdValidTraceId(traceId)

        expectThat(traceIdHex).isNull()
    }
}
