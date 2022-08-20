package com.atkinsondev.opentelemetry.build

import io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_NAME

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.resources.Resource
import java.util.concurrent.TimeUnit

class OpenTelemetryInit {
    fun init(endpoint: String, headers: Map<String, String>, serviceName: String): OpenTelemetry {
        val resource: Resource = Resource.getDefault()
            .merge(Resource.builder().put(SERVICE_NAME, serviceName).build())

        val spanExporterBuilder = OtlpGrpcSpanExporter.builder()
            .setTimeout(2, TimeUnit.SECONDS)
            .setEndpoint(endpoint)

        if (headers.isNotEmpty()) {
            headers.forEach { (key, value) -> spanExporterBuilder.addHeader(key, value) }
        }

        val openTelemetrySdk = OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .setResource(resource)
                    .addSpanProcessor(
                        BatchSpanProcessor.builder(spanExporterBuilder.build())
                            .setScheduleDelay(100, TimeUnit.MILLISECONDS)
                            .build()
                    )
                    .build()
            )
            .buildAndRegisterGlobal()

        Runtime.getRuntime()
            .addShutdownHook(Thread { openTelemetrySdk.sdkTracerProvider.shutdown() })

        return openTelemetrySdk
    }
}
