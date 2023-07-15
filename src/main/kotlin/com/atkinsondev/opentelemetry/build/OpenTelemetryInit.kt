package com.atkinsondev.opentelemetry.build

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes.*
import org.gradle.api.logging.Logger
import java.util.concurrent.TimeUnit

class OpenTelemetryInit(private val logger: Logger) {
    fun init(
        endpoint: String,
        headers: Map<String, String>,
        serviceName: String,
        exporterMode: OpenTelemetryExporterMode,
    ): OpenTelemetrySdk {
        val customResourceAttributes = Resource.builder()
            .put(SERVICE_NAME, serviceName)
            .put(TELEMETRY_SDK_NAME, sdkName)
            .put(TELEMETRY_SDK_VERSION, sdkVersion)
            .build()

        val resource: Resource = Resource.getDefault()
            .merge(customResourceAttributes)

        val spanExporter = when (exporterMode) {
            OpenTelemetryExporterMode.GRPC -> {
                val spanExporterBuilder = OtlpGrpcSpanExporter.builder()
                    .setTimeout(2, TimeUnit.SECONDS)
                    .setEndpoint(endpoint)
                    .addHeader("User-Agent", userAgentValue)

                headers.forEach { (key, value) -> spanExporterBuilder.addHeader(key, value) }

                spanExporterBuilder.build()
            }

            OpenTelemetryExporterMode.ZIPKIN -> {
                val spanExporterBuilder = ZipkinSpanExporter.builder()
                    .setReadTimeout(2, TimeUnit.SECONDS)
                    .setEndpoint(endpoint)

                spanExporterBuilder.build()
            }

            else -> {
                val spanExporterBuilder = OtlpHttpSpanExporter.builder()
                    .setTimeout(2, TimeUnit.SECONDS)
                    .setEndpoint(endpoint)
                    .addHeader("User-Agent", userAgentValue)

                headers.forEach { (key, value) -> spanExporterBuilder.addHeader(key, value) }

                spanExporterBuilder.build()
            }
        }

        logger.info("Registering OpenTelemetry with mode $exporterMode")

        val openTelemetrySdk = OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .setResource(resource)
                    .addSpanProcessor(
                        BatchSpanProcessor.builder(spanExporter)
                            .setScheduleDelay(100, TimeUnit.MILLISECONDS)
                            .build(),
                    )
                    .build(),
            )
            .build()

        return openTelemetrySdk
    }

    companion object {
        const val sdkName = "gradle-opentelemetry-build-plugin"
        const val sdkVersion = "1.5.0" // TODO: Find a way to pull this from the Gradle file
        const val userAgentValue = "$sdkName/$sdkVersion"
    }
}
