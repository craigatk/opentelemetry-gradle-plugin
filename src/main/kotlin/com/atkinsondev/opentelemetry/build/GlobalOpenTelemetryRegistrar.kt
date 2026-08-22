package com.atkinsondev.opentelemetry.build

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.TracerProvider
import io.opentelemetry.sdk.OpenTelemetrySdk
import org.gradle.api.logging.Logger

/**
 * Registers the SDK the plugin builds as the [GlobalOpenTelemetry] instance, so code outside the plugin -
 * such as Gradle init scripts - can obtain a working tracer and add spans to the build trace instead of
 * silently receiving a no-op tracer.
 */
object GlobalOpenTelemetryRegistrar {
    private var registeredSdk: OpenTelemetrySdk? = null

    /**
     * Registers [openTelemetrySdk] globally, returning whether it became the global instance.
     *
     * An instance registered by something else, such as the OpenTelemetry Java agent, is left in place.
     */
    @Synchronized
    fun register(
        openTelemetrySdk: OpenTelemetrySdk,
        logger: Logger,
    ): Boolean =
        try {
            if (GlobalOpenTelemetry.isSet() && registeredSdk == null && !globalIsNoop()) {
                logger.info(ALREADY_REGISTERED_MESSAGE)

                false
            } else {
                // The global is either unset, the no-op instance, or an SDK this plugin registered during an
                // earlier build in the same Gradle daemon and has since shut down - all safe to replace.
                // resetForTest is the only public way to clear the global, since set() throws when one is present.
                GlobalOpenTelemetry.resetForTest()
                GlobalOpenTelemetry.set(openTelemetrySdk)

                registeredSdk = openTelemetrySdk

                true
            }
        } catch (e: Exception) {
            logger.warn(REGISTRATION_ERROR_MESSAGE, e)

            false
        }

    /** Clears the global instance if it is [openTelemetrySdk], so a shut-down SDK is not left registered. */
    @Synchronized
    fun unregister(openTelemetrySdk: OpenTelemetrySdk) {
        if (registeredSdk === openTelemetrySdk) {
            GlobalOpenTelemetry.resetForTest()

            registeredSdk = null
        }
    }

    private fun globalIsNoop(): Boolean = GlobalOpenTelemetry.get().tracerProvider === TracerProvider.noop()

    const val ALREADY_REGISTERED_MESSAGE =
        "A GlobalOpenTelemetry instance is already registered by something other than the OpenTelemetry build plugin - leaving it in place."

    const val REGISTRATION_ERROR_MESSAGE = "Error registering the OpenTelemetry SDK as the global instance"
}
