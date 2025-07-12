package com.atkinsondev.opentelemetry.build

import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.ERROR_KEY
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.ERROR_MESSAGE_KEY
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.FAILURE_MESSAGE_KEY
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.FAILURE_STACKTRACE_KEY
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.TASK_NAME_KEY
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.TEST_FAILURE_SPAN_EVENT_NAME_KEY
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.TEST_NAME_KEY
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.TEST_RESULT_KEY
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

class OpenTelemetryTestListener(
    private val tracer: Tracer,
    private val testTaskSpan: Span,
    private val baggage: Baggage,
    private val testTaskName: String,
    private val nestedTestSpans: Boolean,
    private val logger: Logger = Logging.getLogger(OpenTelemetryTestListener::class.java),
) : TestListener {
    private val stackTraceMaxDepth = 5

    private val testSpanMap = ConcurrentHashMap<TestDescriptor, Span>()

    // 1: <gradle test task> (DecoratingTestDescriptor -> TestMainAction$RootTestSuiteDescriptor)
    //    => We already start a span in OpenTelemetryTaskListener, ignore
    // 2: <gradle test worker> (DecoratingTestDescriptor -> WorkerTestClassProcessor$WorkerTestSuiteDescriptor)
    // 3: <test class> (DecoratingTestDescriptor -> DefaultTestClassDescriptor)
    override fun beforeSuite(test: TestDescriptor) {
        // test.parent != null: ignore TestMainAction$RootTestSuiteDescriptor
        if (test.parent != null && nestedTestSpans) {
            startSpan(test)
        }
    }

    override fun afterSuite(
        test: TestDescriptor,
        testResult: TestResult,
    ) {
        testSpanMap.remove(test)?.end()
    }

    override fun beforeTest(test: TestDescriptor) {
        startSpan(test)
    }

    override fun afterTest(
        test: TestDescriptor,
        testResult: TestResult,
    ) {
        val testResultException = testResult.exception

        if (testResult.resultType == TestResult.ResultType.FAILURE) {
            logger.info("Adding test failure event for test {}", fullDisplayName(test))

            val attributesBuilder =
                Attributes
                    .builder()
                    .put(stringKey(TEST_NAME_KEY), fullDisplayName(test))
                    .put(stringKey(TASK_NAME_KEY), testTaskName)

            if (testResultException != null) {
                attributesBuilder.put(stringKey(FAILURE_MESSAGE_KEY), testResultException.message ?: "")
                attributesBuilder.put(stringKey(FAILURE_STACKTRACE_KEY), truncatedStackTraceString(testResultException, stackTraceMaxDepth))
            }

            testTaskSpan.addEvent(TEST_FAILURE_SPAN_EVENT_NAME_KEY, attributesBuilder.build())
        }

        val span = testSpanMap.remove(test)

        if (testResultException != null) {
            span?.setAttribute(ERROR_KEY, true)
            span?.setAttribute(ERROR_MESSAGE_KEY, testResultException.message ?: "")
            span?.setAttribute(FAILURE_MESSAGE_KEY, testResultException.message ?: "")
            span?.setAttribute(FAILURE_STACKTRACE_KEY, truncatedStackTraceString(testResultException, stackTraceMaxDepth))
        }

        span?.setAttribute(TEST_RESULT_KEY, testResult.resultType.name)

        span?.end()
    }

    private fun startSpan(test: TestDescriptor): Span {
        val parentSpan = test.parent?.let { testSpanMap[it] } ?: testTaskSpan
        val span =
            tracer
                .spanBuilder(fullDisplayName(test))
                .setParent(Context.current().with(parentSpan))
                .addBaggage(baggage)
                .startSpan()
                .setAttribute(TASK_NAME_KEY, testTaskName)
        testSpanMap[test] = span
        return span
    }

    private fun fullDisplayName(test: TestDescriptor): String =
        if (nestedTestSpans) {
            test.displayName
        } else {
            if (test.parent != null) {
                "${test.parent?.displayName} ${test.displayName}"
            } else {
                test.displayName
            }
        }

    companion object {
        fun truncatedStackTraceString(
            t: Throwable,
            maxDepth: Int,
        ): String {
            val stackTraceElements = t.stackTrace.toList()
            val stackTraceElementsToInclude = stackTraceElements.subList(0, min(stackTraceElements.size, maxDepth))
            val stackTraceString = stackTraceElementsToInclude.joinToString("\nat ") + "\n..."

            return stackTraceString
        }
    }
}
