package com.atkinsondev.opentelemetry.build

class SystemEnvironmentSource : EnvironmentSource {
    override fun getenv(name: String): String? = System.getenv(name)
}
