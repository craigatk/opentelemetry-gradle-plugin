package com.atkinsondev.opentelemetry.build.util

object BuildOutputParser {
    fun extractTraceId(buildOutput: String): String {
        val traceId = Regex("OpenTelemetry build trace ID (\\w+)").find(buildOutput)!!.groupValues[1]

        return traceId
    }
}
