package com.atkinsondev.opentelemetry.build

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty

abstract class ZipkinIntegrationTestCase {
    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    // Support setting this port per-test so the tests can be run in parallel without conflicting with each other
    abstract val zipkinPort: Int

    @Serializable
    class ZipkinApiResponseSpan(
        val id: String,
        val parentId: String? = null,
        val name: String,
        val timestamp: Long,
        val localEndpoint: ZipkinApiResponseEndpoint? = null,
        val tags: Map<String, String> = emptyMap(),
    ) {
        fun isRoot(): Boolean = parentId == null

        override fun toString(): String = this.name
    }

    @Serializable
    class ZipkinApiResponseEndpoint(
        val serviceName: String? = null,
    )

    data class ResponseSpan(
        val operationName: String,
        val timestamp: Long,
        val depth: Int,
        val serviceName: String?,
        val children: List<ResponseSpan>,
    ) {
        override fun toString(): String = ">".repeat(depth) + " $operationName"

        fun allStrings(): List<String> = listOf(this.toString()) + children.flatMap { it.allStrings() }

        fun allServiceNames(): List<String?> = listOf(this.serviceName) + children.flatMap { it.allServiceNames() }
    }

    lateinit var zipkinContainer: GenericContainer<*>

    @BeforeEach
    fun setup() {
        zipkinContainer =
            GenericContainer("openzipkin/zipkin:3.6.1")
                .withExposedPorts(zipkinPort)
                .waitingFor(
                    Wait
                        .forHttp("/health")
                        .forStatusCode(200)
                        .forPort(zipkinPort),
                )
        zipkinContainer.start()
    }

    @AfterEach
    fun teardown() {
        zipkinContainer.stop()
    }

    protected fun zipkinSpansEndpoint(): String = "http://localhost:${zipkinContainer.getMappedPort(zipkinPort)}/api/v2/spans"

    protected fun fetchSpanNamesWithDepth(traceId: String): List<String> {
        val rootSpans = fetchRootSpans(traceId)

        return rootSpans.flatMap { it.allStrings() }
    }

    protected fun fetchRootSpans(traceId: String): List<ResponseSpan> {
        val allSpans = fetchTrace(traceId)

        val orderedSpans = allSpans.sortedBy { span -> span.timestamp }

        return findSpansWithParent(orderedSpans, 0, null)
    }

    protected fun fetchTrace(traceId: String): List<ZipkinApiResponseSpan> {
        println("Fetching trace $traceId")

        val httpClient = OkHttpClient.Builder().build()
        val request =
            Request
                .Builder()
                .url("http://localhost:${zipkinContainer.getMappedPort(zipkinPort)}/api/v2/trace/$traceId")
                .get()
                .build()

        var responseBodyStr = ""

        await().untilAsserted {
            val resp = httpClient.newCall(request).execute()
            expectThat(resp.code).isEqualTo(200)

            responseBodyStr = resp.body!!.string()

            val spans = json.decodeFromString(ListSerializer(ZipkinApiResponseSpan.serializer()), responseBodyStr)
            expectThat(spans).isNotEmpty()
        }

        return json.decodeFromString(ListSerializer(ZipkinApiResponseSpan.serializer()), responseBodyStr)
    }

    private fun findSpansWithParent(
        allSpans: List<ZipkinApiResponseSpan>,
        depth: Int,
        parentId: String?,
    ): List<ResponseSpan> {
        val spansWithParent = allSpans.filter { it.parentId == parentId }

        return if (spansWithParent.isNotEmpty()) {
            spansWithParent.map { span ->
                ResponseSpan(
                    operationName = span.name,
                    timestamp = span.timestamp,
                    depth = depth,
                    serviceName = span.localEndpoint?.serviceName,
                    children = findSpansWithParent(allSpans, depth + 1, span.id),
                )
            }
        } else {
            listOf()
        }
    }
}
