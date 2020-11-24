package no.nav.su.se.bakover.client.nais

import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.WiremockBase.Companion.wireMockServer
import no.nav.su.se.bakover.domain.nais.LeaderPodLookupFeil
import org.junit.jupiter.api.Test

internal class LeaderPodLookupClientTest : WiremockBase {

    private val endpoint = "/am/i/the/leader"
    private val localHostName = "localhost"

    @Test
    fun `sier ja når leader elector-pod svarer at vårt hostname er leader`() {
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlPathEqualTo(endpoint))
                .willReturn(
                    WireMock.ok(
                        """
                            {
                              "name": $localHostName
                            }
                        """.trimIndent()
                    )
                )
        )

        LeaderPodLookupClient.amITheLeader("${wireMockServer.baseUrl()}$endpoint", localHostName) shouldBeRight true
    }

    @Test
    fun `sier nei når leader elector-pod svarer at noen andre er leader`() {
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlPathEqualTo(endpoint))
                .willReturn(
                    WireMock.ok(
                        """
                            {
                              "name": "foooooo"
                            }
                        """.trimIndent()
                    )
                )
        )

        LeaderPodLookupClient.amITheLeader("${wireMockServer.baseUrl()}$endpoint", localHostName) shouldBeRight false
    }

    @Test
    fun `håndterer ugyldig json`() {
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlPathEqualTo(endpoint))
                .willReturn(
                    WireMock.ok(
                        """
                            {
                              "foo": "bar"
                            }
                        """.trimIndent()
                    )
                )
        )

        LeaderPodLookupClient.amITheLeader(
            "${wireMockServer.baseUrl()}$endpoint", localHostName
        ) shouldBeLeft LeaderPodLookupFeil.UkjentSvarFraLeaderElectorContainer
    }
}
