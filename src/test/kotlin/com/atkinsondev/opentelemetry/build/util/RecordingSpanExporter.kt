package com.atkinsondev.opentelemetry.build.util

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter

class RecordingSpanExporter : SpanExporter {
    val spanNames: MutableList<String> = mutableListOf()

    override fun export(spans: MutableCollection<SpanData>): CompletableResultCode {
        spanNames.addAll(spans.map { it.name })

        return CompletableResultCode.ofSuccess()
    }

    override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()

    override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
}
