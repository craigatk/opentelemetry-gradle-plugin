package com.atkinsondev.opentelemetry.build

class SystemEnvironmentSource : EnvironmentSource {
    override fun getenv(name: String): String? {
        return System.getenv(name)
    }
}
