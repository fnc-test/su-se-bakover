package no.nav.su.se.bakover.client.kabal

import no.nav.su.se.bakover.common.serialize
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class KabalRequestTest {
    val request = KabalRequestTestData.request
    val fnr = KabalRequestTestData.fnr

    @Test
    fun `serialisering av requesten`() {
        val actual = serialize(request)
        val expected = """
    {
        "avsenderEnhet": "4815",
        "avsenderSaksbehandlerIdent": "123456",
        "dvhReferanse": "dvhReferanse",
        "fagsak": {
            "fagsystem": "SUPSTONAD",
            "fagsakId": "2021"
        },
        "hjemler": [
          {
            "kapittel": 9,
            "lov": "FOLKETRYGDLOVEN",
            "paragraf": 1
          }
        ],
        "innsendtTilNav": "2021-01-01",
        "mottattFoersteinstans": "2021-01-01",
        "innsynUrl": null,
        "kilde": "su-se-bakover",
        "kildeReferanse": "klageId",
        "klager": {
          "id": {
            "type": "PERSON",
            "verdi": "$fnr"
          },
          "skalKlagerMottaKopi": false
        },
        "tilknyttedeJournalposter": [
          {
            "journalpostId": "journalpostId1",
            "type": "OVERSENDELSESBREV"
          },
          {
            "journalpostId": "journalpostId2",
            "type": "BRUKERS_KLAGE"
          }
        ],
        "kommentar": null,
        "frist": null,
        "sakenGjelder": null,
        "oversendtKaDato": null,
        "type": "KLAGE",
        "ytelse": "" 
    }
        """.trimIndent()

        JSONAssert.assertEquals(expected, actual, true)
    }
}
