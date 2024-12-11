package com.atkinsondev.opentelemetry.build.service

import com.atkinsondev.opentelemetry.build.service.model.TestExecutionResult
import org.gradle.api.logging.Logging
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.util.concurrent.CopyOnWriteArrayList

abstract class TestExecutionTrackerService : BuildService<BuildServiceParameters.None> {
    private val logger = Logging.getLogger(TestExecutionTrackerService::class.java)

    private val testExecutions: CopyOnWriteArrayList<TestExecutionResult> = CopyOnWriteArrayList()

    fun addTestExecution(result: TestExecutionResult) {
        logger.debug("Test execution tracker: adding test execution result: $this")

        testExecutions.add(result)
    }

    fun getTestExecutionsForTask(taskPath: String): List<TestExecutionResult> {
        logger.debug("Test execution tracker: getting test executions: $this")

        return testExecutions.filter { it.taskPath == taskPath }
    }
}
