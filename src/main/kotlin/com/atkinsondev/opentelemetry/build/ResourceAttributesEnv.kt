package com.atkinsondev.opentelemetry.build

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Reads resource attributes from the standard OpenTelemetry environment variable / system property,
 * per https://opentelemetry.io/docs/specs/otel/resource/sdk/#specifying-resource-information-via-an-environment-variable
 */
object ResourceAttributesEnv {
    const val ENV_VAR_NAME = "OTEL_RESOURCE_ATTRIBUTES"
    const val SYSTEM_PROPERTY_NAME = "otel.resource.attributes"

    fun fromEnvironment(
        environmentSource: EnvironmentSource,
        systemProperty: (String) -> String? = System::getProperty,
    ): Map<String, String> {
        val rawValue = systemProperty(SYSTEM_PROPERTY_NAME) ?: environmentSource.getenv(ENV_VAR_NAME)

        return parse(rawValue)
    }

    fun parse(rawValue: String?): Map<String, String> {
        if (rawValue.isNullOrBlank()) {
            return emptyMap()
        }

        return rawValue
            .split(",")
            .mapNotNull { entry ->
                val trimmedEntry = entry.trim()
                if (trimmedEntry.isEmpty()) return@mapNotNull null

                val separatorIndex = trimmedEntry.indexOf('=')
                if (separatorIndex <= 0) return@mapNotNull null

                val key = trimmedEntry.substring(0, separatorIndex).trim()
                val encodedValue = trimmedEntry.substring(separatorIndex + 1).trim()

                key to URLDecoder.decode(encodedValue, StandardCharsets.UTF_8)
            }.toMap()
    }
}
