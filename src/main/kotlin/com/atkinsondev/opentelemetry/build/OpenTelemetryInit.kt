package com.atkinsondev.opentelemetry.build

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_NAME
import java.util.concurrent.TimeUnit

class OpenTelemetryInit {
    fun init(endpoint: String, headers: Map<String, String>, serviceName: String, exporterMode: OpenTelemetryExporterMode): OpenTelemetry {
        val resource: Resource = Resource.getDefault()
            .merge(Resource.builder().put(SERVICE_NAME, serviceName).build())

        val spanExporter = if (exporterMode == OpenTelemetryExporterMode.GRPC) {
            val spanExporterBuilder = OtlpGrpcSpanExporter.builder()
                .setTimeout(2, TimeUnit.SECONDS)
                .setEndpoint(endpoint)

            headers.forEach { (key, value) -> spanExporterBuilder.addHeader(key, value) }

            spanExporterBuilder.build()
        } else {
            val spanExporterBuilder = OtlpHttpSpanExporter.builder()
                .setTimeout(2, TimeUnit.SECONDS)
                .setEndpoint(endpoint)

            headers.forEach { (key, value) -> spanExporterBuilder.addHeader(key, value) }

            spanExporterBuilder.build()
        }

        val openTelemetrySdk = OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .setResource(resource)
                    .addSpanProcessor(
                        BatchSpanProcessor.builder(spanExporter)
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
