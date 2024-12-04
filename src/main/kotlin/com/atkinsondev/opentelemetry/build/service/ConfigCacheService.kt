package com.atkinsondev.opentelemetry.build.service

import org.gradle.api.configuration.BuildFeatures
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import javax.inject.Inject

abstract class ConfigCacheService : BuildService<ConfigCacheService.Params> {
    interface Params : BuildServiceParameters {
        @Inject
        fun getBuildFeatures(): BuildFeatures
    }

    fun configCacheRequested(): Boolean = parameters.getBuildFeatures().configurationCache.requested.getOrElse(false)
}
