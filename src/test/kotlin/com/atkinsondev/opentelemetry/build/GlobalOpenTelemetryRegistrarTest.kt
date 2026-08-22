package com.atkinsondev.opentelemetry.build

import com.atkinsondev.opentelemetry.build.util.RecordingSpanExporter
import io.mockk.mockk
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.gradle.api.logging.Logger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.*

class GlobalOpenTelemetryRegistrarTest {
    private val logger = mockk<Logger>(relaxed = true)

    private val createdSdks = mutableListOf<OpenTelemetrySdk>()

    @BeforeEach
    @AfterEach
    fun resetGlobalOpenTelemetry() {
        createdSdks.forEach { GlobalOpenTelemetryRegistrar.unregister(it) }
        createdSdks.clear()

        GlobalOpenTelemetry.resetForTest()
    }

    @Test
    fun `should register SDK so global tracer records spans`() {
        val exporter = RecordingSpanExporter()

        val registered = GlobalOpenTelemetryRegistrar.register(sdkWith(exporter), logger)

        GlobalOpenTelemetry
            .getTracer("init-script")
            .spanBuilder("init-script-span")
            .startSpan()
            .end()

        expectThat(registered).isTrue()
        expectThat(exporter.spanNames).contains("init-script-span")
    }

    @Test
    fun `should register SDK when the global instance was already pinned to the no-op instance`() {
        // Reading the global before the plugin initializes pins it to the no-op instance
        GlobalOpenTelemetry.get()

        val exporter = RecordingSpanExporter()

        val registered = GlobalOpenTelemetryRegistrar.register(sdkWith(exporter), logger)

        GlobalOpenTelemetry
            .getTracer("init-script")
            .spanBuilder("init-script-span")
            .startSpan()
            .end()

        expectThat(registered).isTrue()
        expectThat(exporter.spanNames).contains("init-script-span")
    }

    @Test
    fun `should replace SDK registered by an earlier build in the same Gradle daemon`() {
        val firstBuildExporter = RecordingSpanExporter()
        GlobalOpenTelemetryRegistrar.register(sdkWith(firstBuildExporter), logger)

        val secondBuildExporter = RecordingSpanExporter()
        val registered = GlobalOpenTelemetryRegistrar.register(sdkWith(secondBuildExporter), logger)

        GlobalOpenTelemetry
            .getTracer("init-script")
            .spanBuilder("second-build-span")
            .startSpan()
            .end()

        expectThat(registered).isTrue()
        expectThat(secondBuildExporter.spanNames).contains("second-build-span")
        expectThat(firstBuildExporter.spanNames).isEmpty()
    }

    @Test
    fun `should leave a global instance registered by something else in place`() {
        val otherExporter = RecordingSpanExporter()
        GlobalOpenTelemetry.set(sdkWith(otherExporter))

        val pluginExporter = RecordingSpanExporter()
        val registered = GlobalOpenTelemetryRegistrar.register(sdkWith(pluginExporter), logger)

        GlobalOpenTelemetry
            .getTracer("init-script")
            .spanBuilder("init-script-span")
            .startSpan()
            .end()

        expectThat(registered).isFalse()
        expectThat(otherExporter.spanNames).contains("init-script-span")
        expectThat(pluginExporter.spanNames).isEmpty()
    }

    @Test
    fun `should unregister SDK so a shut-down SDK is not left as the global instance`() {
        val openTelemetrySdk = sdkWith(RecordingSpanExporter())

        GlobalOpenTelemetryRegistrar.register(openTelemetrySdk, logger)
        GlobalOpenTelemetryRegistrar.unregister(openTelemetrySdk)

        expectThat(GlobalOpenTelemetry.isSet()).isFalse()
    }

    @Test
    fun `should not unregister an SDK that is not the global instance`() {
        val registeredSdk = sdkWith(RecordingSpanExporter())
        GlobalOpenTelemetryRegistrar.register(registeredSdk, logger)

        GlobalOpenTelemetryRegistrar.unregister(sdkWith(RecordingSpanExporter()))

        expectThat(GlobalOpenTelemetry.isSet()).isTrue()
    }

    private fun sdkWith(exporter: RecordingSpanExporter): OpenTelemetrySdk =
        OpenTelemetrySdk
            .builder()
            .setTracerProvider(
                SdkTracerProvider
                    .builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build(),
            ).build()
            .also { createdSdks.add(it) }
}
