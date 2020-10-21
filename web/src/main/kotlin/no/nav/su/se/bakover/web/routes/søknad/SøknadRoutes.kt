package no.nav.su.se.bakover.web.routes.søknad

import SuMetrics
import arrow.core.Either
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.service.søknad.KunneIkkeLukkeSøknad
import no.nav.su.se.bakover.service.søknad.KunneIkkeOppretteSøknad
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withSøknadId

internal const val søknadPath = "/soknad"

@KtorExperimentalAPI
internal fun Route.søknadRoutes(
    søknadService: SøknadService
) {
    authorize(Brukerrolle.Veileder, Brukerrolle.Saksbehandler) {
        post(søknadPath) {
            Either.catch { deserialize<SøknadInnholdJson>(call) }.fold(
                ifLeft = {
                    call.application.environment.log.info(it.message, it)
                    call.svar(BadRequest.message("Ugyldig body"))
                },
                ifRight = {
                    søknadService.nySøknad(it.toSøknadInnhold()).fold(
                        { kunneIkkeOppretteSøknad ->
                            call.svar(
                                when (kunneIkkeOppretteSøknad) {
                                    KunneIkkeOppretteSøknad.FantIkkePerson -> HttpStatusCode.NotFound.message("Fant ikke person")
                                }
                            )
                        },
                        { sak ->
                            SuMetrics.Counter.Søknad.increment()
                            call.audit("Lagrer søknad for person: $sak")
                            call.svar(
                                Resultat.json(Created, serialize((sak.toJson())))
                            )
                        }
                    )
                }
            )
        }
    }
    authorize(Brukerrolle.Saksbehandler) {
        post("$søknadPath/{søknadId}/trekk") {
            call.withSøknadId { søknadId ->
                søknadService.trekkSøknad(
                    søknadId = søknadId,
                    saksbehandler = Saksbehandler(call.suUserContext.getNAVIdent()),
                    begrunnelse = ""
                ).fold(
                    ifLeft = {
                        when (it) {
                            is KunneIkkeLukkeSøknad.SøknadErAlleredeLukket ->
                                call.svar(BadRequest.message("Søknad er allerede trukket"))
                            is KunneIkkeLukkeSøknad.SøknadHarEnBehandling ->
                                call.svar(BadRequest.message("Søknaden har en behandling"))
                            is KunneIkkeLukkeSøknad.FantIkkeSøknad ->
                                call.svar(BadRequest.message("Fant ikke søknad for $søknadId"))
                        }
                    },
                    ifRight = {
                        call.audit("Lukket søknad for søknad: $søknadId")
                        call.svar(Resultat.json(HttpStatusCode.OK, serialize((it.toJson()))))
                    }
                )
            }
        }
    }
}
