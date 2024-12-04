package com.atkinsondev.opentelemetry.build

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gradle.api.logging.Logger
import org.junit.jupiter.api.Test

class TraceLoggerTest {
    @Test
    fun `when trace view url set should replace trace ID and log full URL`() {
        val traceId = "5a47e977c06a5d2a046d101fdedcb4ed"

        val logger = mockk<Logger>()

        every { logger.warn(any<String>()) } returns Unit

        val traceLogger = TraceLogger("http://localhost:16686/trace/{traceId}", null, logger)

        traceLogger.logTrace(traceId)

        verify { logger.warn("\nOpenTelemetry build trace ID 5a47e977c06a5d2a046d101fdedcb4ed") }

        verify { logger.warn("\nOpenTelemetry build trace http://localhost:16686/trace/5a47e977c06a5d2a046d101fdedcb4ed") }
    }

    @Test
    fun `when trace view type set and trace view URL does not end with slash should replace trace ID and log full URL`() {
        val traceId = "5a47e977c06a5d2a046d101fdedcb4ef"

        val logger = mockk<Logger>()

        every { logger.warn(any<String>()) } returns Unit

        val traceLogger = TraceLogger("http://localhost:16686", TraceViewType.JAEGER, logger)

        traceLogger.logTrace(traceId)

        verify { logger.warn("\nOpenTelemetry build trace ID 5a47e977c06a5d2a046d101fdedcb4ef") }
        verify { logger.warn("\nOpenTelemetry build trace http://localhost:16686/trace/5a47e977c06a5d2a046d101fdedcb4ef") }
    }

    @Test
    fun `when trace view type set and trace view URL ends with slash should replace trace ID and log full URL`() {
        val traceId = "5a47e977c06a5d2a046d101fdedcb4ef"

        val logger = mockk<Logger>()

        every { logger.warn(any<String>()) } returns Unit

        val traceLogger = TraceLogger("http://localhost:16686/", TraceViewType.JAEGER, logger)

        traceLogger.logTrace(traceId)

        verify { logger.warn("\nOpenTelemetry build trace ID 5a47e977c06a5d2a046d101fdedcb4ef") }
        verify { logger.warn("\nOpenTelemetry build trace http://localhost:16686/trace/5a47e977c06a5d2a046d101fdedcb4ef") }
    }
}
