package com.atkinsondev.opentelemetry.build

import org.gradle.api.logging.Logger

class TraceLogger(private val logger: Logger, private val traceViewUrl: String?, private val traceViewType: TraceViewType?) {
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
