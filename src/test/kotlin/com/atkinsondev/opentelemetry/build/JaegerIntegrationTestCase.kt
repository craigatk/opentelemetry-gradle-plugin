package com.atkinsondev.opentelemetry.build

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import strikt.api.expectThat
import strikt.assertions.isEqualTo

abstract class JaegerIntegrationTestCase {
    protected val json =
        Json {
            ignoreUnknownKeys = true
        }

    // Support setting these ports per-test so the tests can be run in parallel without conflicting with each other
    abstract val healthCheckPort: Int
    abstract val queryPort: Int
    abstract val oltpGrpcPort: Int

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

    lateinit var jaegerContainer: GenericContainer<*>

    @BeforeEach
    fun setup() {
        jaegerContainer =
            GenericContainer("jaegertracing/all-in-one:1.57")
                .withExposedPorts(
                    healthCheckPort,
                    oltpGrpcPort,
                    queryPort,
                )
                .withEnv(
                    mapOf(
                        "ADMIN_HTTP_HOST_PORT" to ":$healthCheckPort",
                        "QUERY_HTTP_SERVER_HOST_PORT" to ":$queryPort",
                        "COLLECTOR_OTLP_GRPC_HOST_PORT" to ":$oltpGrpcPort",
                    ),
                )
                .waitingFor(
                    Wait.forHttp("/")
                        .forStatusCode(200)
                        .forPort(healthCheckPort),
                )
        jaegerContainer.start()
    }

    @AfterEach
    fun teardown() {
        jaegerContainer.stop()
    }

    fun fetchSpansWithDepth(traceId: String): List<String> {
        // Fetch trace data from Jaeger
        val httpClient = OkHttpClient.Builder().build()
        val request =
            Request.Builder()
                .url("http://localhost:${jaegerContainer.getMappedPort(queryPort)}/api/traces/$traceId")
                .get()
                .build()
        val resp = httpClient.newCall(request).execute()
        expectThat(resp.code).isEqualTo(200)

        val orderedSpansNamesWithDepth = extractSpansWithDepth(resp.body!!.string())

        return orderedSpansNamesWithDepth
    }

    fun extractSpansWithDepth(responseBodyString: String): List<String> {
        val decoded = json.decodeFromString(JaegerApiResponse.serializer(), responseBodyString)
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

        return orderedSpansNamesWithDepth
    }
}
