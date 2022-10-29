package com.atkinsondev.opentelemetry.build

import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.errorKey
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.failureMessageKey
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.failureStacktraceKey
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.taskNameKey
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.testFailureSpanEventName
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.testNameKey
import com.atkinsondev.opentelemetry.build.OpenTelemetryBuildSpanData.testResultKey
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import org.gradle.api.internal.tasks.testing.AbstractTestDescriptor
import org.gradle.api.logging.Logger
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
    private val logger: Logger,
) : TestListener {
    private val stackTraceMaxDepth = 5

    private val testSpanMap = ConcurrentHashMap<String, Span>()

    override fun beforeSuite(test: TestDescriptor) { }

    override fun afterSuite(test: TestDescriptor, testResult: TestResult) { }

    override fun beforeTest(test: TestDescriptor) {
        val testKey = testHashKey(test)

        val span = tracer
            .spanBuilder(fullDisplayName(test))
            .setParent(Context.current().with(testTaskSpan))
            .addBaggage(baggage)
            .startSpan()
            .setAttribute(taskNameKey, testTaskName)

        testSpanMap[testKey] = span
    }

    override fun afterTest(test: TestDescriptor, testResult: TestResult) {
        val testKey = testHashKey(test)

        val testResultException = testResult.exception

        if (testResult.resultType == TestResult.ResultType.FAILURE) {
            logger.info("Adding test failure event for test {}", fullDisplayName(test))

            val attributesBuilder = Attributes.builder()
                .put(stringKey(testNameKey), fullDisplayName(test))
                .put(stringKey(taskNameKey), testTaskName)

            if (testResultException != null) {
                attributesBuilder.put(stringKey(failureMessageKey), testResultException.message ?: "")
                attributesBuilder.put(stringKey(failureStacktraceKey), truncatedStackTraceString(testResultException, stackTraceMaxDepth))
            }

            testTaskSpan.addEvent(testFailureSpanEventName, attributesBuilder.build())
        }

        val span = testSpanMap[testKey]

        if (testResultException != null) {
            span?.setAttribute(errorKey, testResultException.message ?: "")
            span?.setAttribute(failureMessageKey, testResultException.message ?: "")
            span?.setAttribute(failureStacktraceKey, truncatedStackTraceString(testResultException, stackTraceMaxDepth))
        }

        span?.setAttribute(testResultKey, testResult.resultType.name)

        span?.end()
    }

    companion object {
        fun truncatedStackTraceString(t: Throwable, maxDepth: Int): String {
            val stackTraceElements = t.stackTrace.toList()
            t.stackTraceToString()
            val stackTraceElementsToInclude = stackTraceElements.subList(0, min(stackTraceElements.size, maxDepth))
            val stackTraceString = stackTraceElementsToInclude.joinToString("\nat ") + "\n..."

            return stackTraceString
        }

        fun testHashKey(test: TestDescriptor): String {
            return if (test is AbstractTestDescriptor) {
                test.id.toString()
            } else {
                test.displayName
            }
        }

        fun fullDisplayName(test: TestDescriptor): String =
            if (test.parent != null) {
                "${test.parent?.displayName} ${test.displayName}"
            } else {
                test.displayName
            }
    }
}
