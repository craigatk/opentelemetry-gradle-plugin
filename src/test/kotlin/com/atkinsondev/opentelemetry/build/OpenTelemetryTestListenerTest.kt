package com.atkinsondev.opentelemetry.build

import com.atkinsondev.opentelemetry.build.util.FakeSpanBuilder
import com.atkinsondev.opentelemetry.build.util.FakeTracer
import com.atkinsondev.opentelemetry.build.util.NoopLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.trace.Span
import org.gradle.api.internal.tasks.testing.DefaultTestDescriptor
import org.gradle.api.internal.tasks.testing.DefaultTestFailure
import org.gradle.api.internal.tasks.testing.DefaultTestFailureDetails
import org.gradle.api.internal.tasks.testing.results.DefaultTestResult
import org.gradle.api.tasks.testing.TestResult
import org.junit.jupiter.api.Test

class OpenTelemetryTestListenerTest {
    @Test
    fun `when test fails should set error and error message attributes`() {
        val testTaskSpan = mockk<Span>(relaxed = true)

        val span = mockk<Span>()

        every { span.setAttribute(any(String::class), any(Boolean::class)) } returns span
        every { span.setAttribute(any(String::class), any(String::class)) } returns span
        every { span.end() } returns Unit

        val spanBuilder = FakeSpanBuilder(span)

        val openTelemetryTestListener =
            OpenTelemetryTestListener(
                tracer = FakeTracer(spanBuilder),
                testTaskSpan = testTaskSpan,
                baggage = Baggage.builder().build(),
                testTaskName = "test",
                logger = NoopLogger(),
                nestedTestSpans = false,
            )

        val testDescriptor = DefaultTestDescriptor(":test", "test.task.Klass", "failing test")

        val testFailureDetails =
            DefaultTestFailureDetails(
                "test failure message",
                "failure.Klass",
                "stacktrace",
                true,
                false,
                "true",
                "false",
                "true".toByteArray(),
                "false".toByteArray(),
            )

        val testResult =
            DefaultTestResult(
                TestResult.ResultType.FAILURE,
                109123,
                109723,
                1,
                0,
                1,
                listOf(DefaultTestFailure(RuntimeException("test failure"), testFailureDetails, listOf())),
            )

        openTelemetryTestListener.beforeTest(testDescriptor)
        openTelemetryTestListener.afterTest(testDescriptor, testResult)

        verify { span.setAttribute(OpenTelemetryBuildSpanData.ERROR_KEY, true) }
        verify { span.setAttribute(OpenTelemetryBuildSpanData.ERROR_MESSAGE_KEY, "test failure") }
    }

    @Test
    fun `when test passes should not set error and error message attributes`() {
        val testTaskSpan = mockk<Span>(relaxed = true)

        val span = mockk<Span>()

        every { span.setAttribute(any(String::class), any(Boolean::class)) } returns span
        every { span.setAttribute(any(String::class), any(String::class)) } returns span
        every { span.end() } returns Unit

        val spanBuilder = FakeSpanBuilder(span)

        val openTelemetryTestListener =
            OpenTelemetryTestListener(
                tracer = FakeTracer(spanBuilder),
                testTaskSpan = testTaskSpan,
                baggage = Baggage.builder().build(),
                testTaskName = "test",
                logger = NoopLogger(),
                nestedTestSpans = false,
            )

        val testDescriptor = DefaultTestDescriptor(":test", "test.task.Klass", "passing test")

        val testResult =
            DefaultTestResult(
                TestResult.ResultType.SUCCESS,
                109123,
                109723,
                1,
                1,
                0,
                listOf(),
            )

        openTelemetryTestListener.beforeTest(testDescriptor)
        openTelemetryTestListener.afterTest(testDescriptor, testResult)

        verify(exactly = 0) { span.setAttribute(OpenTelemetryBuildSpanData.ERROR_KEY, any(Boolean::class)) }
        verify(exactly = 0) { span.setAttribute(OpenTelemetryBuildSpanData.ERROR_MESSAGE_KEY, any(String::class)) }
    }
}
