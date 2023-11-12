package com.atkinsondev.opentelemetry.build

fun interface EnvironmentSource {
    fun getenv(name: String): String?
}
