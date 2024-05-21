package com.atkinsondev.opentelemetry.build

import com.atkinsondev.opentelemetry.build.util.FakeSpanBuilder
import com.atkinsondev.opentelemetry.build.util.FakeTracer
import com.atkinsondev.opentelemetry.build.util.NoopLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.trace.*
import org.gradle.api.internal.tasks.TaskExecutionOutcome
import org.gradle.api.internal.tasks.TaskStateInternal
import org.junit.jupiter.api.Test

class OpenTelemetryTaskListenerTest {
    @Test
    fun `when test task fails should set error flag and error message attributes`() {
        val rootSpan = mockk<Span>(relaxed = true)

        val span = mockk<Span>()

        every { span.setAttribute(any(String::class), any(Boolean::class)) } returns span
        every { span.setAttribute(any(String::class), any(String::class)) } returns span
        every { span.end() } returns Unit

        val spanBuilder = FakeSpanBuilder(span)

        val taskListener =
            OpenTelemetryTaskListener(
                tracer = FakeTracer(spanBuilder),
                rootSpan = rootSpan,
                baggage = Baggage.builder().build(),
                logger = NoopLogger(),
                nestedTestSpans = false,
            )

        val testTask = mockk<org.gradle.api.tasks.testing.Test>(relaxed = true)
        every { testTask.path } returns ":proj:test"
        every { testTask.name } returns "test"

        val taskState = TaskStateInternal()
        val exception = RuntimeException("failure message")
        taskState.setOutcome(exception)

        taskListener.beforeExecute(testTask)
        taskListener.afterExecute(testTask, taskState)

        verify { span.setAttribute(OpenTelemetryBuildSpanData.ERROR_KEY, true) }
        verify { span.setAttribute(OpenTelemetryBuildSpanData.ERROR_MESSAGE_KEY, "failure message") }
    }

    @Test
    fun `when test task pass should not set error flag and error message attributes`() {
        val rootSpan = mockk<Span>(relaxed = true)

        val span = mockk<Span>()

        every { span.setAttribute(any(String::class), any(Boolean::class)) } returns span
        every { span.setAttribute(any(String::class), any(String::class)) } returns span
        every { span.end() } returns Unit

        val spanBuilder = FakeSpanBuilder(span)

        val taskListener =
            OpenTelemetryTaskListener(
                tracer = FakeTracer(spanBuilder),
                rootSpan = rootSpan,
                baggage = Baggage.builder().build(),
                logger = NoopLogger(),
                nestedTestSpans = false,
            )

        val testTask = mockk<org.gradle.api.tasks.testing.Test>(relaxed = true)
        every { testTask.path } returns ":proj:test"
        every { testTask.name } returns "test"

        val taskState = TaskStateInternal()
        taskState.outcome = TaskExecutionOutcome.EXECUTED

        taskListener.beforeExecute(testTask)
        taskListener.afterExecute(testTask, taskState)

        verify(exactly = 0) { span.setAttribute(OpenTelemetryBuildSpanData.ERROR_KEY, true) }
        verify(exactly = 0) { span.setAttribute(OpenTelemetryBuildSpanData.ERROR_MESSAGE_KEY, "failure message") }
    }
}
