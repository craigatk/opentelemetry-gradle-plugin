package com.atkinsondev.opentelemetry.build

import org.gradle.api.provider.Property

abstract class OpenTelemetryBuildPluginExtension {
    abstract val endpoint: Property<String>
    abstract val headers: Property<Map<String, String>>
}