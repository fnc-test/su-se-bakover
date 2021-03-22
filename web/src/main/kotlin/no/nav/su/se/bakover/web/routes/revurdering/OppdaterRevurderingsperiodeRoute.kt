package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.service.revurdering.KunneIkkeOppdatereRevurderingsperiode
import no.nav.su.se.bakover.service.revurdering.OppdaterRevurderingRequest
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.fantIkkeRevurdering
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.ugyldigTilstand
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId
import java.time.LocalDate

@KtorExperimentalAPI
internal fun Route.oppdaterRevurderingRoute(
    revurderingService: RevurderingService,
) {
    data class Body(
        val fraOgMed: LocalDate,
        val årsak: String,
        val begrunnelse: String,
    )

    suspend fun oppdater(call: ApplicationCall) {
        call.withRevurderingId { revurderingId ->
            call.withBody<Body> { body ->
                val navIdent = call.suUserContext.navIdent

                revurderingService.oppdaterRevurderingsperiode(
                    OppdaterRevurderingRequest(
                        revurderingId = revurderingId,
                        fraOgMed = body.fraOgMed,
                        årsak = body.årsak,
                        begrunnelse = body.begrunnelse,
                        saksbehandler = NavIdentBruker.Saksbehandler(navIdent),
                    ),
                ).fold(
                    ifLeft = { call.svar(it.tilResultat()) },
                    ifRight = {
                        call.audit("Oppdaterte perioden på revurdering med id: $revurderingId")
                        call.svar(Resultat.json(HttpStatusCode.Created, serialize(it.toJson())))
                    },
                )
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        // TODO jah: Slett denne når su-se-framover har byttet til ny path og er i prod.
        post("$revurderingPath/{revurderingId}/oppdaterPeriode") {
            oppdater(call)
        }
        post("$revurderingPath/{revurderingId}/oppdater") {
            oppdater(call)
        }
    }
}

private fun KunneIkkeOppdatereRevurderingsperiode.tilResultat(): Resultat {
    return when (this) {
        is KunneIkkeOppdatereRevurderingsperiode.UgyldigPeriode -> GenerelleRevurderingsfeilresponser.ugyldigPeriode(
            this.subError,
        )
        is KunneIkkeOppdatereRevurderingsperiode.FantIkkeRevurdering -> fantIkkeRevurdering
        is KunneIkkeOppdatereRevurderingsperiode.UgyldigTilstand -> ugyldigTilstand(this.fra, this.til)
        is KunneIkkeOppdatereRevurderingsperiode.PeriodenMåVæreInnenforAlleredeValgtStønadsperiode -> HttpStatusCode.BadRequest.errorJson(
            "Perioden må være innenfor allerede valgt stønadsperiode",
            "perioden_må_være_innenfor_stønadsperioden",
        )
        KunneIkkeOppdatereRevurderingsperiode.UgyldigBegrunnelse -> HttpStatusCode.BadRequest.errorJson(
            "Begrunnelse kan ikke være tom",
            "begrunnelse_kan_ikke_være_tom",
        )
        KunneIkkeOppdatereRevurderingsperiode.UgyldigÅrsak -> HttpStatusCode.BadRequest.errorJson(
            "Ugyldig årsak, må være en av: ${Revurderingsårsak.Årsak.values()}",
            "ugyldig_årsak",
        )
    }
}
