package com.atkinsondev.opentelemetry.build.service

import com.atkinsondev.opentelemetry.build.service.model.TestExecutionResult
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.util.concurrent.CopyOnWriteArrayList

abstract class TestExecutionTrackerService : BuildService<TestExecutionTrackerService.Params> {
    interface Params : BuildServiceParameters

    private val testExecutions: CopyOnWriteArrayList<TestExecutionResult> = CopyOnWriteArrayList()

    fun addTestExecution(result: TestExecutionResult) {
        testExecutions.add(result)
    }

    fun getTestExecutionsForTask(taskPath: String): List<TestExecutionResult> = testExecutions.filter { it.taskPath == taskPath }
}
