package no.nav.su.se.bakover

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.application.Application
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.config.ApplicationConfig
import io.ktor.features.*
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.WWWAuthenticate
import io.ktor.http.HttpHeaders.XRequestId
import io.ktor.http.HttpMethod.Companion.Options
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Locations
import io.ktor.request.path
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.CollectorRegistry
import no.nav.su.se.bakover.azure.AzureClient
import no.nav.su.se.bakover.azure.getJWKConfig
import no.nav.su.se.bakover.db.DataSourceBuilder
import no.nav.su.se.bakover.inntekt.SuInntektClient
import no.nav.su.se.bakover.inntekt.inntektRoutes
import no.nav.su.se.bakover.person.SuPersonClient
import no.nav.su.se.bakover.person.personRoutes
import org.json.JSONObject
import org.slf4j.MDC
import org.slf4j.event.Level
import java.net.URL

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
fun Application.susebakover(
        jwkConfig: JSONObject = getJWKConfig(fromEnvironment("azure.wellknownUrl")),
        jwkProvider: JwkProvider = JwkProviderBuilder(URL(jwkConfig.getString("jwks_uri"))).build(),
        personClient: SuPersonClient = SuPersonClient(fromEnvironment("integrations.suPerson.url")),
        inntektClient: SuInntektClient = SuInntektClient(fromEnvironment("integrations.suInntekt.url")),
        azureClient: AzureClient = AzureClient(fromEnvironment("azure.clientId"), fromEnvironment("azure.clientSecret"), jwkConfig.getString("token_endpoint"))
) {

    val dataSourceBuilder = DataSourceBuilder(env)

    install(CORS) {
        method(Options)
        header(Authorization)
        exposeHeader(WWWAuthenticate)
        host(fromEnvironment("cors.allow.origin"), listOf("http", "https"))
    }

    val collectorRegistry = CollectorRegistry.defaultRegistry
    installMetrics(collectorRegistry)
    naisRoutes(collectorRegistry)

    setupAuthentication(
            jwkConfig = jwkConfig,
            jwkProvider = jwkProvider,
            config = environment.config
    )
    oauthRoutes(
            frontendRedirectUrl = fromEnvironment("integrations.suSeFramover.redirectUrl")
    )

    install(Locations)
    routing {

        authenticate("jwt") {
            install(CallId) {
                header(XRequestId)
                generate(17)
            }
            install(CallLogging) {
                level = Level.INFO
                intercept(ApplicationCallPipeline.Monitoring) {
                    MDC.put(XRequestId, call.callId)
                }
                filter { call ->
                    listOf(IS_ALIVE_PATH, IS_READY_PATH, METRICS_PATH).none {
                        call.request.path().startsWith(it)
                    }
                }
            }

            get(path = "/authenticated") {
                val principal = (call.authentication.principal as JWTPrincipal).payload
                call.respond("""
                    {
                        "data": "Congrats ${principal.getClaim("name").asString()}, you are successfully authenticated with a JWT token"
                    }
                """.trimIndent())
            }

            personRoutes(environment.config, azureClient, personClient)
            inntektRoutes(environment.config, azureClient, inntektClient)
        }
    }
}

@KtorExperimentalAPI
fun Application.fromEnvironment(path: String): String = environment.config.property(path).getString()

@KtorExperimentalAPI
fun ApplicationConfig.getProperty(key: String): String = property(key).getString()

fun main(args: Array<String>) = io.ktor.server.netty.EngineMain.main(args)
