package com.atkinsondev.opentelemetry.build

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo

class ResourceAttributesEnvTest {
    @Test
    fun whenNullShouldReturnEmptyMap() {
        expectThat(ResourceAttributesEnv.parse(null)).isEmpty()
    }

    @Test
    fun whenBlankShouldReturnEmptyMap() {
        expectThat(ResourceAttributesEnv.parse("  ")).isEmpty()
    }

    @Test
    fun whenSingleKeyValueShouldParse() {
        expectThat(ResourceAttributesEnv.parse("gitlab.pipeline.id=1234")).isEqualTo(mapOf("gitlab.pipeline.id" to "1234"))
    }

    @Test
    fun whenMultipleKeyValuesShouldParseAll() {
        expectThat(
            ResourceAttributesEnv.parse("gitlab.pipeline.id=1234,gitlab.job.id=5678"),
        ).isEqualTo(mapOf("gitlab.pipeline.id" to "1234", "gitlab.job.id" to "5678"))
    }

    @Test
    fun whenValuesArePercentEncodedShouldDecodeThem() {
        expectThat(
            ResourceAttributesEnv.parse("runner.name=my%20runner"),
        ).isEqualTo(mapOf("runner.name" to "my runner"))
    }

    @Test
    fun whenEntriesHaveExtraWhitespaceShouldTrimIt() {
        expectThat(
            ResourceAttributesEnv.parse(" gitlab.pipeline.id = 1234 , gitlab.job.id = 5678 "),
        ).isEqualTo(mapOf("gitlab.pipeline.id" to "1234", "gitlab.job.id" to "5678"))
    }

    @Test
    fun whenEntryHasNoEqualsSignShouldSkipIt() {
        expectThat(ResourceAttributesEnv.parse("invalid,gitlab.pipeline.id=1234")).isEqualTo(mapOf("gitlab.pipeline.id" to "1234"))
    }

    @Test
    fun fromEnvironmentShouldPreferSystemPropertyOverEnvVar() {
        val environmentSource = EnvironmentSource { name -> if (name == ResourceAttributesEnv.ENV_VAR_NAME) "gitlab.pipeline.id=1234" else null }
        val systemProperty: (String) -> String? = { name -> if (name == ResourceAttributesEnv.SYSTEM_PROPERTY_NAME) "gitlab.job.id=5678" else null }

        expectThat(
            ResourceAttributesEnv.fromEnvironment(environmentSource, systemProperty),
        ).isEqualTo(mapOf("gitlab.job.id" to "5678"))
    }

    @Test
    fun fromEnvironmentShouldFallBackToEnvVarWhenNoSystemProperty() {
        val environmentSource = EnvironmentSource { name -> if (name == ResourceAttributesEnv.ENV_VAR_NAME) "gitlab.pipeline.id=1234" else null }
        val systemProperty: (String) -> String? = { null }

        expectThat(
            ResourceAttributesEnv.fromEnvironment(environmentSource, systemProperty),
        ).isEqualTo(mapOf("gitlab.pipeline.id" to "1234"))
    }
}
