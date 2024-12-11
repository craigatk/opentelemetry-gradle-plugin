package com.atkinsondev.opentelemetry.build

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThan
import strikt.assertions.isNotNull

abstract class JaegerIntegrationTestCase {
    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    // Support setting these ports per-test so the tests can be run in parallel without conflicting with each other
    abstract val healthCheckPort: Int
    abstract val oltpGrpcPort: Int
    abstract val queryPort: Int

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
        val tags: List<JaegerApiResponseTag>,
    ) {
        fun parentSpanId(): String? = references.firstOrNull()?.spanID

        fun isRoot(): Boolean = references.isNullOrEmpty()

        override fun toString(): String = this.operationName
    }

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

    @Serializable
    class JaegerApiResponseTag(
        val key: String,
        val type: String,
        val value: JsonElement,
    ) {
        fun toResponseSpanTag(): ResponseSpanTag =
            ResponseSpanTag(
                key = key,
                type = type,
                boolValue = if (type == "bool") value.jsonPrimitive.booleanOrNull else null,
                strValue = if (type == "string") value.jsonPrimitive.content else null,
            )
    }

    class SpanWithDepth(
        val operationName: String,
        val startTime: Long,
        val depth: Int,
    ) {
        fun expectThatSpanIsAfter(
            earlierSpanName: String,
            spansWithDepth: List<SpanWithDepth>,
        ) {
            val earlierSpan = spansWithDepth.find { it.operationName == earlierSpanName }
            expectThat(earlierSpan).isNotNull()

            expectThat(startTime).isGreaterThan(earlierSpan!!.startTime)
        }
    }

    data class ResponseSpan(
        val operationName: String,
        val startTime: Long,
        val depth: Int,
        val tags: List<ResponseSpanTag>,
        val children: List<ResponseSpan>,
    ) {
        override fun toString(): String = ">".repeat(depth) + " $operationName"

        fun allStrings(): List<String> = listOf(this.toString()) + children.flatMap { it.allStrings() }
    }

    data class ResponseSpanTag(
        val key: String,
        val type: String,
        val boolValue: Boolean?,
        val strValue: String?,
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

    protected fun fetchSpanNamesWithDepth(traceId: String): List<String> {
        val rootSpans = fetchRootSpans(traceId)

        return rootSpans.flatMap { it.allStrings() }
    }

    protected fun fetchRootSpans(traceId: String): List<ResponseSpan> {
        val apiResponse = fetchTrace(traceId)

        val allSpans = apiResponse.data.flatMap { it.spans }
        val orderedSpans = allSpans.sortedBy { span -> span.startTime }

        val rootSpans = findSpansWithParent(orderedSpans, 0, null)

        return rootSpans
    }

    protected fun fetchTrace(traceId: String): JaegerApiResponse {
        println("Fetching trace $traceId")
        // Fetch trace data from Jaeger
        val httpClient = OkHttpClient.Builder().build()
        val request =
            Request.Builder()
                .url("http://localhost:${jaegerContainer.getMappedPort(queryPort)}/api/traces/$traceId")
                .get()
                .build()

        await().untilAsserted {
            val resp = httpClient.newCall(request).execute()
            expectThat(resp.code).isEqualTo(200)
        }

        val resp = httpClient.newCall(request).execute()
        val responseBodyStr = resp.body!!.string()

        val apiResponse = json.decodeFromString(JaegerApiResponse.serializer(), responseBodyStr)

        return apiResponse
    }

    protected fun fetchSpansWithDepth(traceId: String): List<SpanWithDepth> {
        val apiResponse = fetchTrace(traceId)

        val orderedSpansWithDepth = extractSpansWithDepth(apiResponse)

        return orderedSpansWithDepth
    }

    private fun findSpansWithParent(
        allSpans: List<JaegerApiResponseSpan>,
        depth: Int,
        parentSpanId: String?,
    ): List<ResponseSpan> {
        val spansWithParent = allSpans.filter { it.parentSpanId() == parentSpanId }

        return if (spansWithParent.isNotEmpty()) {
            spansWithParent.map { span ->
                ResponseSpan(
                    operationName = span.operationName,
                    startTime = span.startTime,
                    depth = depth,
                    tags = span.tags.map(JaegerApiResponseTag::toResponseSpanTag),
                    children = findSpansWithParent(allSpans, depth + 1, span.spanID),
                )
            }
        } else {
            listOf()
        }
    }

    private fun extractSpansWithDepth(apiResponse: JaegerApiResponse): List<SpanWithDepth> {
        // Lazy as the order isn't always correct (e.g. the result returned by Jaeger returns a child span before the parent)
        val depths = mutableMapOf<String, Int>()

        val allSpans = apiResponse.data.flatMap { it.spans }
        val orderedSpans = allSpans.sortedBy { span -> span.startTime }

        val orderedSpansWithDepth =
            orderedSpans.map { span ->
                val depth =
                    if (span.references.isEmpty()) {
                        0
                    } else {
                        val parentSpanId =
                            span.references
                                .also {
                                    assert(it.size == 1)
                                }
                                .first()
                                .spanID

                        if (depths.containsKey(parentSpanId)) {
                            depths[parentSpanId]!! + 1
                        } else {
                            0
                        }
                    }
                depths[span.spanID] = depth
                SpanWithDepth(
                    operationName = span.operationName,
                    startTime = span.startTime,
                    depth = depth,
                )
            }

        return orderedSpansWithDepth
    }
}
