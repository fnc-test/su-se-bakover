package no.nav.su.se.bakover.client.dokdistfordeling

import arrow.core.right
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import dokument.domain.Distribusjonstidspunkt
import dokument.domain.Distribusjonstype
import dokument.domain.brev.BrevbestillingId
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.WiremockBase.Companion.wireMockServer
import no.nav.su.se.bakover.client.stubs.sts.TokenOppslagStub
import no.nav.su.se.bakover.common.journal.JournalpostId
import org.junit.jupiter.api.Test

internal class DokDistFordelingClientTest : WiremockBase {
    private val journalpostId = JournalpostId("1")
    private val distribusjonstype = Distribusjonstype.VEDTAK
    private val distribusjonstidspunkt = Distribusjonstidspunkt.KJERNETID
    private val client = DokDistFordelingClient(wireMockServer.baseUrl(), TokenOppslagStub)

    private val requestBody = client.byggDistribusjonPostJson(journalpostId, distribusjonstype, distribusjonstidspunkt)

    @Test
    fun `should complete order for distribution`() {
        wireMockServer.stubFor(
            wiremockBuilder
                .withRequestBody(WireMock.equalToJson(requestBody))
                .willReturn(
                    WireMock.okJson(
                        """
                        {
                            "bestillingsId": "id på tingen"
                        }
                        """.trimIndent(),
                    ),
                ),
        )
        client.bestillDistribusjon(journalpostId, distribusjonstype, distribusjonstidspunkt) shouldBe BrevbestillingId("id på tingen").right()
    }
    private val wiremockBuilder: MappingBuilder = WireMock.post(WireMock.urlPathEqualTo(DOK_DIST_FORDELING_PATH))
        .withHeader("Authorization", WireMock.equalTo("Bearer token"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("Nav-CallId", WireMock.equalTo("correlationId"))
}
