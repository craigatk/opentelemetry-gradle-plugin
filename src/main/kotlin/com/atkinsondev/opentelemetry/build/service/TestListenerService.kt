package com.atkinsondev.opentelemetry.build.service

import com.atkinsondev.opentelemetry.build.service.model.TestExecutionFailure
import com.atkinsondev.opentelemetry.build.service.model.TestExecutionResult
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import kotlin.math.min

class TestListenerService(
    private val testTaskPath: String,
    private val testTaskName: String,
    private val nestedTestSpans: Boolean,
    private val testExecutionTrackerService: Provider<TestExecutionTrackerService>,
) : TestListener {
    private val stackTraceMaxDepth = 5

    override fun beforeSuite(test: TestDescriptor) {
    }

    override fun afterSuite(
        test: TestDescriptor,
        testResult: TestResult,
    ) {
        if (nestedTestSpans && !test.displayName.contains("Gradle Test Run")) {
            recordTestExecution(test, testResult)
        }
    }

    override fun beforeTest(test: TestDescriptor) {
    }

    override fun afterTest(
        test: TestDescriptor,
        testResult: TestResult,
    ) {
        recordTestExecution(test, testResult)
    }

    private fun recordTestExecution(
        test: TestDescriptor,
        testResult: TestResult,
    ) {
        val failure: TestExecutionFailure? =
            if (testResult.resultType == TestResult.ResultType.FAILURE) {
                val testResultException = testResult.exception

                TestExecutionFailure(
                    message = testResultException?.message,
                    stackTrace = truncatedStackTraceString(testResultException, stackTraceMaxDepth),
                )
            } else {
                null
            }

        val testExecutionResult =
            TestExecutionResult(
                testName = test.displayName,
                testDisplayName = test.fullDisplayName(nestedTestSpans),
                taskPath = testTaskPath,
                taskName = testTaskName,
                startTime = testResult.startTime,
                endTime = testResult.endTime,
                failure = failure,
                parentTestName = test.parent?.displayName,
            )

        testExecutionTrackerService.get().addTestExecution(testExecutionResult)
    }

    companion object {
        fun truncatedStackTraceString(
            t: Throwable?,
            maxDepth: Int,
        ): String? =
            t?.let {
                val stackTraceElements = t.stackTrace.toList()
                val stackTraceElementsToInclude = stackTraceElements.subList(0, min(stackTraceElements.size, maxDepth))
                val stackTraceString = stackTraceElementsToInclude.joinToString("\nat ") + "\n..."

                return stackTraceString
            }
    }
}

fun TestDescriptor.fullDisplayName(nestedTestSpans: Boolean): String =
    if (!nestedTestSpans && this.parent != null) {
        "${this.parent!!.displayName} ${this.displayName}"
    } else {
        this.displayName
    }
