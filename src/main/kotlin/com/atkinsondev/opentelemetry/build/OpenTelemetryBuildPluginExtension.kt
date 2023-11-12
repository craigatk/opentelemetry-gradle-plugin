package com.atkinsondev.opentelemetry.build

import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

abstract class OpenTelemetryBuildPluginExtension {
    abstract val endpoint: Property<String>
    abstract val headers: MapProperty<String, String>
    abstract val serviceName: Property<String>
    abstract val exporterMode: Property<OpenTelemetryExporterMode>
    abstract val enabled: Property<Boolean>
    abstract val customTags: MapProperty<String, String>
    abstract val parentSpanIdEnvVarName: Property<String>
    abstract val parentTraceIdEnvVarName: Property<String>

    init {
        exporterMode.convention(OpenTelemetryExporterMode.GRPC)
        enabled.convention(true)
        parentSpanIdEnvVarName.convention("SPAN_ID")
        parentTraceIdEnvVarName.convention("TRACE_ID")
    }
}
