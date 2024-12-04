package com.atkinsondev.opentelemetry.build

import com.atkinsondev.opentelemetry.build.RemoteParentTracer.createRemoteSpanContext
import com.atkinsondev.opentelemetry.build.RemoteParentTracer.createValidSpanId
import com.atkinsondev.opentelemetry.build.RemoteParentTracer.createdValidTraceId
import io.opentelemetry.api.trace.SpanContext
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property

object ParentSpan {
    fun parentSpanContext(
        parentSpanIdEnvVarName: Property<String>,
        parentTraceIdEnvVarName: Property<String>,
        environmentSource: EnvironmentSource,
        logger: Logger = Logging.getLogger(ParentSpan::class.java),
    ): SpanContext? {
        // Create a parent context passed in from a CI system like Jenkins to tie the Gradle trace
        // with one created by a parent system.
        // Ref https://github.com/open-telemetry/opentelemetry-java/discussions/4668
        if (parentSpanIdEnvVarName.isPresent && parentTraceIdEnvVarName.isPresent) {
            logger.info(
                "Reading parent span ID from environment variable {} and trace ID from environment variable {}",
                parentSpanIdEnvVarName.get(),
                parentTraceIdEnvVarName.get(),
            )

            val parentSpanIdStr = environmentSource.getenv(parentSpanIdEnvVarName.get())
            val parentTraceIdStr = environmentSource.getenv(parentTraceIdEnvVarName.get())

            val parentSpanIdHex = createValidSpanId(parentSpanIdStr)
            val parentTraceIdHex = createdValidTraceId(parentTraceIdStr)

            if (parentSpanIdHex != null && parentTraceIdHex != null) {
                val remoteSpanContext = createRemoteSpanContext(parentTraceIdHex, parentSpanIdHex)

                if (remoteSpanContext.isValid) {
                    logger.info("Using parent span ID {} and parent trace ID {}", parentSpanIdStr, parentTraceIdStr)

                    return remoteSpanContext
                } else {
                    logger.warn("Remote span context is not valid. Parent span ID: {} - parent trace ID: {}", parentSpanIdStr, parentTraceIdStr)
                }
            }

            if (parentSpanIdStr != null && parentSpanIdHex == null) {
                logger.info("Received invalid parent span ID {}", parentSpanIdStr)
            }

            if (parentTraceIdStr != null && parentTraceIdHex == null) {
                logger.info("Received invalid parent trace ID {}", parentTraceIdStr)
            }
        }

        return null
    }
}
