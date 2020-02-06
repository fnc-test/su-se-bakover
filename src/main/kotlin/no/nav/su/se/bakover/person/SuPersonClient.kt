package no.nav.su.se.bakover.person

import com.github.kittinunf.fuel.httpGet
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.XRequestId
import no.nav.su.se.bakover.Feil
import no.nav.su.se.bakover.Ok
import no.nav.su.se.bakover.Result
import no.nav.su.se.bakover.azure.AzureClient
import no.nav.su.se.bakover.inntekt.Persontilgang
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC

internal class SuPersonClient(suPersonBaseUrl: String, private val suPersonClientId: String, private val azure: AzureClient) :
    Persontilgang {
    private val personResource = "$suPersonBaseUrl/person"
    private val suPersonIdentLabel = "ident"

    override fun person(ident: String, innloggetSaksbehandlerToken: String): Result {
        val onBehalfOfToken = azure.onBehalfOFToken(innloggetSaksbehandlerToken, suPersonClientId)
        val (_, _, result) = personResource.httpGet(listOf(suPersonIdentLabel to ident))
            .header(Authorization, "Bearer $onBehalfOfToken")
            .header(XRequestId, MDC.get(XRequestId))
            .responseString()
        return result.fold(
            { Ok(it) },
            {
                val errorMessage = it.response.body().asString(ContentType.Application.Json.toString())
                val statusCode = it.response.statusCode
                logger.debug("Kall mot PDL feilet, statuskode: $statusCode, feilmelding: $errorMessage");
                Feil(statusCode, errorMessage)
            }
        )
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(SuPersonClient::class.java)
    }
}

