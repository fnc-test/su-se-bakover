package no.nav.su.se.bakover.web.søknadsbehandling.flyktning

import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.TestApplicationEngine
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest

/**
- [resultat] se [no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon.Flyktning.Status]
 */
internal fun TestApplicationEngine.leggTilFlyktningstatus(
    sakId: String,
    behandlingId: String,
    resultat: String = "VilkårOppfylt",
    begrunnelse: String = "Vurdering av flyktningstatus er lagt til automatisk av LeggTilFlyktningstatus.kt",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
): String {

    return runBlocking {
        defaultRequest(
            method = HttpMethod.Patch,
            uri = "/saker/$sakId/behandlinger/$behandlingId/informasjon",
            roller = listOf(brukerrolle),
        ) {
            setBody(
                //language=JSON
                """
                  {
                    "flyktning":{
                      "status":"$resultat",
                      "begrunnelse":"$begrunnelse"
                    },
                    "lovligOpphold":null,
                    "fastOppholdINorge":null,
                    "institusjonsopphold":null,
                    "formue":null,
                    "personligOppmøte":null
                  }
                """.trimIndent(),
            )
        }.apply {
            status shouldBe HttpStatusCode.OK
            contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
        }.bodyAsText()
    }
}
