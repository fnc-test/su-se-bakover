package no.nav.su.se.bakover.web.søknadsbehandling.fastopphold

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest

internal fun leggTilFastOppholdINorge(
    sakId: String,
    behandlingId: String,
    fraOgMed: String,
    tilOgMed: String,
    body: () -> String = { innvilgetFastOppholdJson(fraOgMed, tilOgMed) },
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    url: String = "/saker/$sakId/behandlinger/$behandlingId/fastopphold",
    client: HttpClient,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            url,
            listOf(brukerrolle),
            client = client,
        ) {
            setBody(body())
        }.apply {
            withClue("body=${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.Created
                contentType() shouldBe ContentType.parse("application/json")
            }
        }.bodyAsText()
    }
}

internal fun innvilgetFastOppholdJson(fraOgMed: String, tilOgMed: String): String {
    return """
       [
          {
            "periode": {
              "fraOgMed": "$fraOgMed",
              "tilOgMed": "$tilOgMed"
            },
            "vurdering": "VilkårOppfylt"
          }
        ]
    """.trimIndent()
}

internal fun avslåttFastOppholdJson(fraOgMed: String, tilOgMed: String): String {
    return """
       [
          {
            "periode": {
              "fraOgMed": "$fraOgMed",
              "tilOgMed": "$tilOgMed"
            },
            "vurdering": "VilkårIkkeOppfylt"
          }
        ]
    """.trimIndent()
}
