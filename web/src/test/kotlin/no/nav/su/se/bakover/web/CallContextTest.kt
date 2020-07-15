package no.nav.su.se.bakover.web

import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.ContentType
import io.ktor.http.HttpHeaders.XCorrelationId
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.Parameters
import io.ktor.http.RequestConnectionPoint
import io.ktor.http.headersOf
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.request.ApplicationReceivePipeline
import io.ktor.request.ApplicationRequest
import io.ktor.request.RequestCookies
import io.ktor.request.header
import io.ktor.response.ApplicationResponse
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.ktor.util.AttributeKey
import io.ktor.util.Attributes
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.io.ByteReadChannel
import no.nav.su.se.bakover.client.ClientResponse
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder.build
import no.nav.su.se.bakover.web.routes.søknad.SøknadInnholdJson.Companion.toSøknadInnholdJson
import no.nav.su.se.bakover.web.routes.søknad.søknadPath
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import java.util.Collections.synchronizedList
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
internal class CallContextTest {

    @Test
    fun `parallel requests should preserve context`() {
        val numRequests = 100
        val downstreamCorrelationIds: MutableList<String> = synchronizedList(mutableListOf<String>())

        withTestApplication({
            testEnv()
            testSusebakover(httpClients = buildHttpClients(personOppslag = object :
                PersonOppslag {
                override fun person(ident: Fnr): ClientResponse = throw NotImplementedError()

                override fun aktørId(ident: Fnr): String =
                    "aktørid".also { downstreamCorrelationIds.add(MDC.get("X-Correlation-ID")) }
            }))
        }) {
            val requests = List(numRequests) { CallableRequest(this, it, Jwt.create()) }
            val executors = Executors.newFixedThreadPool(numRequests)
            var applicationCalls: List<TestApplicationCall>? = requests
                .map { executors.submit(it) }
                .map { it.get() }

            val passedCorrelationIds = List(numRequests) { it.toString() }
            assertEquals(numRequests, downstreamCorrelationIds.size, "downstreamCorrelationIds")
            assertTrue(downstreamCorrelationIds.containsAll(passedCorrelationIds)) // Verify all correlation ids passed along to service to get aktørid
            applicationCalls!!.forEach {
                assertEquals(
                    it.request.header(XCorrelationId),
                    it.response.headers[XCorrelationId]
                )
            }
        }
    }

    internal class CallableRequest(
        val testApplicationEngine: TestApplicationEngine,
        val correlationId: Int,
        val token: String
    ) : Callable<TestApplicationCall> {
        private val søknadInnhold: SøknadInnhold = build(personopplysninger = SøknadInnholdTestdataBuilder.personopplysninger(FnrGenerator.random().toString()))

        private val søknadInnholdJson: String = objectMapper.writeValueAsString(søknadInnhold.toSøknadInnholdJson())

        override fun call(): TestApplicationCall {
            println("Test Thread: ${Thread.currentThread()}")
            return testApplicationEngine.handleRequest(Post,
                søknadPath
            ) {
                addHeader(XCorrelationId, "$correlationId")
                addHeader(Authorization, token)
                addHeader(ContentType, Json.toString())
                setBody(søknadInnholdJson)
            }
        }
    }

    class callWithAuth(val token: String, val correlationId: String) : ApplicationCall {
        override val application: Application
            get() = throw NotImplementedError()
        override val attributes: Attributes
            get() = MyAttributes
        override val parameters: Parameters
            get() = throw NotImplementedError()
        override val request: ApplicationRequest
            get() = DummyRequest(token)
        override val response: ApplicationResponse
            get() = throw NotImplementedError()
    }

    object MyAttributes : Attributes {
        override val allKeys: List<AttributeKey<*>>
            get() = throw NotImplementedError()

        override fun <T : Any> computeIfAbsent(key: AttributeKey<T>, block: () -> T): T = throw NotImplementedError()
        override fun contains(key: AttributeKey<*>): Boolean = throw NotImplementedError()
        override fun <T : Any> getOrNull(key: AttributeKey<T>): T? = DEFAULT_CALL_ID as T
        override fun <T : Any> put(key: AttributeKey<T>, value: T): Unit = throw NotImplementedError()
        override fun <T : Any> remove(key: AttributeKey<T>): Unit = throw NotImplementedError()
    }

    class DummyRequest(val token: String) : ApplicationRequest {
        override val call: ApplicationCall
            get() = throw NotImplementedError()
        override val cookies: RequestCookies
            get() = throw NotImplementedError()
        override val headers: Headers
            get() = headersOf(Pair(Authorization, listOf(token)), Pair(XCorrelationId, listOf(DEFAULT_CALL_ID)))
        override val local: RequestConnectionPoint
            get() = throw NotImplementedError()
        override val pipeline: ApplicationReceivePipeline
            get() = throw NotImplementedError()
        override val queryParameters: Parameters
            get() = throw NotImplementedError()

        override fun receiveChannel(): ByteReadChannel {
            throw NotImplementedError()
        }
    }
}
