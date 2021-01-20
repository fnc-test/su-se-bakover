package no.nav.su.se.bakover.web.routes.drift

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.patch
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.behandling.BehandlingService
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.drift.FixIverksettingerResponseJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.drift.FixSøknaderResponseJson.Companion.toJson

internal const val DRIFT_PATH = "/drift"

@KtorExperimentalAPI
internal fun Route.driftRoutes(
    søknadService: SøknadService,
    behandlingService: BehandlingService,
) {
    authorize(Brukerrolle.Drift) {
        patch("$DRIFT_PATH/søknader/fix") {
            søknadService.opprettManglendeJournalpostOgOppgave().let {
                call.respond(
                    if (it.harFeil()) HttpStatusCode.InternalServerError else HttpStatusCode.OK,
                    serialize(it.toJson())
                )
            }
        }
    }

    authorize(Brukerrolle.Drift) {
        patch("$DRIFT_PATH/iverksettinger/fix") {
            behandlingService.opprettManglendeJournalpostOgBrevdistribusjon().let {
                call.respond(
                    if (it.harFeil()) HttpStatusCode.InternalServerError else HttpStatusCode.OK,
                    serialize(it.toJson())
                )
            }
        }
    }
}