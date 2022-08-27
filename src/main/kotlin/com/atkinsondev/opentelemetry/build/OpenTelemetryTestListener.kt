package com.atkinsondev.opentelemetry.build

import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import kotlin.math.min

class OpenTelemetryTestListener(private val logger: Logger, private val testTaskSpan: Span) : TestListener {
    private val stackTraceMaxDepth = 5

    override fun beforeSuite(test: TestDescriptor) { }

    override fun afterSuite(test: TestDescriptor, testResult: TestResult) { }

    override fun beforeTest(test: TestDescriptor) { }

    override fun afterTest(test: TestDescriptor, testResult: TestResult) {
        if (testResult.resultType == TestResult.ResultType.FAILURE) {
            logger.info("Adding test failure event for test {}", test.displayName)

            val attributesBuilder = Attributes.builder()
                .put(stringKey("test.name"), test.displayName)

            val testResultException = testResult.exception

            if (testResultException != null) {
                attributesBuilder.put(stringKey("failure.message"), testResultException.message ?: "")
                attributesBuilder.put(stringKey("failure.stacktrace"), truncatedStackTraceString(testResultException, stackTraceMaxDepth))
            }

            testTaskSpan.addEvent("test.failure", attributesBuilder.build())
        }
    }

    companion object {
        fun truncatedStackTraceString(t: Throwable, maxDepth: Int): String {
            val stackTraceElements = t.stackTrace.toList()
            t.stackTraceToString()
            val stackTraceElementsToInclude = stackTraceElements.subList(0, min(stackTraceElements.size, maxDepth))
            val stackTraceString = stackTraceElementsToInclude.joinToString("\nat ") + "\n..."

            return stackTraceString
        }
    }
}
