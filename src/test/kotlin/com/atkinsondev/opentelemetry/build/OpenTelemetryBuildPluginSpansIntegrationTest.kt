package com.atkinsondev.opentelemetry.build

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertLinesMatch
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import strikt.api.expectThat
import strikt.assertions.*
import java.io.File
import java.nio.file.Path

class OpenTelemetryBuildPluginSpansIntegrationTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    @Serializable
    class JaegerApiResponse(
        // In this test this list always contains a single element
        // Maybe this is a list when querying for multiple traces?
        val data: List<JaegerApiResponseTrace>,
    )

    @Serializable
    class JaegerApiResponseTrace(
        // Flat list of all spans in the trace
        // Order isn't necessarily correct
        val spans: List<JaegerApiResponseSpan>,
    )

    @Serializable
    class JaegerApiResponseSpan(
        val spanID: String,
        val operationName: String,
        val references: List<JaegerApiResponseSpanReference>,
        val startTime: Long,
    )

    @Serializable
    class JaegerApiResponseSpanReference(
        // Not used currently, but we currently only handle CHILD_OF correctly.
        // Decode into enum only containing CHILD_OF to get an exception when for some reason another relationship type is returned in the future.
        @Suppress("unused")
        val refType: RefType,
        val spanID: String,
    ) {
        @Suppress("unused") // Future
        @Serializable
        enum class RefType {
            CHILD_OF,
        }
    }

    class FlatSpan(
        val operationName: String,
        val startTime: Long,
        val depth: () -> Int,
    )

    companion object {
        private const val OTLP_GRPC_PORT = 4317
        private const val JAEGER_HEALTHCHECK_PORT = 14269
        private const val JAEGER_QUERY_PORT = 16686
    }

    @Test
    fun `check spans`(
        @TempDir projectRootDirPath: Path,
    ) {
        // We could use a different trace backend here as well doesn't really matter
        // Jaeger has a simple API for querying traces.
        // Traces are returned in a proprietary (?) format but that's ok.
        val jaegerContainer =
            GenericContainer("jaegertracing/all-in-one:1.57")
                .withExposedPorts(
                    OTLP_GRPC_PORT,
                    JAEGER_HEALTHCHECK_PORT,
                    JAEGER_QUERY_PORT,
                )
                .waitingFor(
                    Wait.forHttp("/")
                        .forStatusCode(200)
                        .forPort(JAEGER_HEALTHCHECK_PORT),
                )
        jaegerContainer.start()

        val buildFileContents =
            """
            ${baseBuildFileContents()}

            openTelemetryBuild {
                endpoint = 'http://localhost:${jaegerContainer.getMappedPort(OTLP_GRPC_PORT)}'
            }
            """.trimIndent()

        File(projectRootDirPath.toFile(), "build.gradle").writeText(buildFileContents)

        createSrcDirectoryAndClassFile(projectRootDirPath)
        createTestDirectoryAndClassFile(projectRootDirPath)

        val buildResult =
            GradleRunner.create()
                .withProjectDir(projectRootDirPath.toFile())
                .withArguments("test", "--info", "--stacktrace")
                .withPluginClasspath()
                .build()

        expectThat(buildResult.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        // Parse trace ID from build output
        val traceId = Regex("OpenTelemetry build trace ID (\\w+)").find(buildResult.output)!!.groupValues[1]

        // Fetch trace data from Jaeger
        val httpClient = OkHttpClient.Builder().build()
        val request =
            Request.Builder()
                .url("http://localhost:${jaegerContainer.getMappedPort(JAEGER_QUERY_PORT)}/api/traces/$traceId")
                .get()
                .build()
        val resp = httpClient.newCall(request).execute()
        expectThat(resp.code).isEqualTo(200)

        val decoded = json.decodeFromString(JaegerApiResponse.serializer(), resp.body!!.string())
        // Lazy as the order isn't always correct (e.g. the result returned by Jaeger returns a child span before the parent)
        val depths = mutableMapOf<String, () -> Int>()
        val orderedSpans =
            // Even though so far we only expect `data` to contain a single element, a flatMap should make sense here
            decoded.data.flatMap { d ->
                d.spans.map { span ->
                    val depth =
                        if (span.references.isEmpty()) {
                            {
                                0
                            }
                        } else {
                            val parentSpanId =
                                span.references
                                    .also {
                                        assert(it.size == 1)
                                    }
                                    .first()
                                    .spanID
                            {
                                depths[parentSpanId]!!() + 1
                            }
                        }
                    depths[span.spanID] = depth
                    FlatSpan(
                        operationName = span.operationName,
                        startTime = span.startTime,
                        depth = depth,
                    )
                }
            }.sortedBy {
                it.startTime
            }
        val orderedSpansNamesWithDepth =
            orderedSpans.map {
                ">".repeat(it.depth()) + " ${it.operationName}"
            }

        // Use assertLinesMatch, as it has nice support for regexes
        assertLinesMatch(
            listOf(
                " junit\\d+-build",
                "> :compileKotlin",
                "> :processResources",
                "> :processTestResources",
                "> :compileJava",
                "> :classes",
                "> :compileTestKotlin",
                "> :compileTestJava",
                "> :testClasses",
                "> :test",
                ">> FooTest foo should return bar()",
            ),
            orderedSpansNamesWithDepth,
        )
    }
}
