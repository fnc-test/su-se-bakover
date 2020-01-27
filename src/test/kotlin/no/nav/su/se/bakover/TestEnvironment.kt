package no.nav.su.se.bakover

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.github.tomakehurst.wiremock.WireMockServer
import io.ktor.application.Application
import io.ktor.config.MapApplicationConfig
import io.ktor.http.HttpHeaders.XRequestId
import io.ktor.http.HttpMethod
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.util.KtorExperimentalAPI
import io.mockk.every
import io.mockk.mockk
import no.nav.su.se.bakover.azure.AzureClient
import no.nav.su.se.bakover.inntekt.SuInntektClient
import no.nav.su.se.bakover.person.SuPersonClient
import org.json.JSONObject
import java.util.*

const val AZURE_CLIENT_ID = "clientId"
const val AZURE_CLIENT_SECRET = "secret"
const val AZURE_REQUIRED_GROUP = "su-group"
const val AZURE_WELL_KNOWN_URL = "/.well-known"
const val AZURE_JWKS_PATH = "/keys"
const val AZURE_ISSUER = "azure"
const val AZURE_BACKEND_CALLBACK_URL = "/callback"
const val AZURE_TOKEN_URL = "/token"
const val SUBJECT = "enSaksbehandler"
const val SU_PERSON_AZURE_CLIENT_ID = "personClientId"
const val SU_INNTEKT_AZURE_CLIENT_ID = "inntektClientId"
const val SU_FRONTEND_REDIRECT_URL = "auth/complete"
const val SU_FRONTEND_ORIGIN = "localhost"
const val DEFAULT_CALL_ID = "callId"


@KtorExperimentalAPI
fun Application.testEnv(wireMockServer: WireMockServer? = null) {
    val baseUrl = wireMockServer?.baseUrl() ?: SU_FRONTEND_ORIGIN
    (environment.config as MapApplicationConfig).apply {
        put("cors.allow.origin", SU_FRONTEND_ORIGIN)
        put("integrations.suPerson.url", baseUrl)
        put("integrations.suPerson.clientId", SU_PERSON_AZURE_CLIENT_ID)
        put("integrations.suInntekt.url", baseUrl)
        put("integrations.suInntekt.clientId", SU_INNTEKT_AZURE_CLIENT_ID)
        put("integrations.suSeFramover.redirectUrl", "$baseUrl$SU_FRONTEND_REDIRECT_URL")
        put("azure.requiredGroup", AZURE_REQUIRED_GROUP)
        put("azure.clientId", AZURE_CLIENT_ID)
        put("azure.clientSecret", AZURE_CLIENT_SECRET)
        put("azure.wellknownUrl", "$baseUrl$AZURE_WELL_KNOWN_URL")
        put("azure.backendCallbackUrl", "$baseUrl$AZURE_BACKEND_CALLBACK_URL")
        put("issuer", AZURE_ISSUER)
    }
}

val jwtStub = JwtStub()

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
fun Application.usingMocks(
        jwkConfig: JSONObject = mockk(relaxed = true),
        jwkProvider: JwkProvider = mockk(relaxed = true),
        personClient: SuPersonClient = mockk(relaxed = true),
        inntektClient: SuInntektClient = mockk(relaxed = true),
        azureClient: AzureClient = mockk(relaxed = true)
) {
    val e = Base64.getEncoder().encodeToString(jwtStub.publicKey.publicExponent.toByteArray())
    val n = Base64.getEncoder().encodeToString(jwtStub.publicKey.modulus.toByteArray())
    every {
        jwkProvider.get(any())
    }.returns(Jwk("key-1234", "RSA", "RS256", null, emptyList(), null, null, null, mapOf("e" to e, "n" to n)))
    every {
        jwkConfig.getString("issuer")
    }.returns(AZURE_ISSUER)

    susebakover(
            jwkConfig = jwkConfig,
            jwkProvider = jwkProvider,
            personClient = personClient,
            inntektClient = inntektClient,
            azureClient = azureClient
    )
}

fun TestApplicationEngine.withCallId(
        method: HttpMethod,
        uri: String,
        setup: TestApplicationRequest.() -> Unit = {}
): TestApplicationCall {
    return handleRequest(method, uri) {
        addHeader(XRequestId, DEFAULT_CALL_ID)
        setup()
    }
}
