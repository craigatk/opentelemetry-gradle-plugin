package com.atkinsondev.opentelemetry.build

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class TraceLogger(private val traceViewUrl: String?, private val traceViewType: TraceViewType?, private val logger: Logger = Logging.getLogger(TraceLogger::class.java)) {
    fun logTrace(traceId: String) {
        logger.warn("\nOpenTelemetry build trace ID $traceId")

        if (traceViewUrl != null) {
            val traceViewUrlPattern: String =
                when (traceViewType) {
                    TraceViewType.JAEGER -> if (traceViewUrl.endsWith("/")) "${traceViewUrl}trace/{traceId}" else "$traceViewUrl/trace/{traceId}"
                    else -> traceViewUrl
                }

            val fullTraceViewUrl = traceViewUrlPattern.replace("{traceId}", traceId)

            logger.warn("\nOpenTelemetry build trace $fullTraceViewUrl")
        }
    }
}
