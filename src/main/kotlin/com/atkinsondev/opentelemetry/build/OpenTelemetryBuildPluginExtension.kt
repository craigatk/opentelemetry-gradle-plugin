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

    abstract val nestedTestSpans: Property<Boolean>

    abstract val traceViewUrl: Property<String>
    abstract val traceViewType: Property<TraceViewType>

    abstract val supportConfigCache: Property<Boolean>

    abstract val taskTraceEnvironmentEnabled: Property<Boolean>
    abstract val taskTraceEnvironmentSpanIdName: Property<String>
    abstract val taskTraceEnvironmentTraceIdName: Property<String>

    init {
        exporterMode.convention(OpenTelemetryExporterMode.GRPC)
        enabled.convention(true)

        parentSpanIdEnvVarName.convention("SPAN_ID")
        parentTraceIdEnvVarName.convention("TRACE_ID")

        nestedTestSpans.convention(true)

        supportConfigCache.convention(false)

        taskTraceEnvironmentEnabled.convention(false)
        taskTraceEnvironmentSpanIdName.convention("SPAN_ID")
        taskTraceEnvironmentTraceIdName.convention("TRACE_ID")
    }
}
