package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import arrow.core.sequence
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.periode.PeriodeJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilBosituasjongrunnlag
import no.nav.su.se.bakover.service.revurdering.LeggTilBosituasjonRequest
import no.nav.su.se.bakover.service.revurdering.LeggTilBosituasjonerRequest
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.Feilresponser.ugyldigBody
import no.nav.su.se.bakover.web.routes.grunnlag.tilResultat
import no.nav.su.se.bakover.web.routes.periode.toPeriodeOrResultat
import no.nav.su.se.bakover.web.routes.søknadsbehandling.tilResultat
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId
import no.nav.su.se.bakover.web.withSakId
import java.util.UUID

private data class JsonRequest(
    val bosituasjoner: List<JsonBody>,
) {
    fun toService(revurderingId: UUID): Either<Resultat, LeggTilBosituasjonerRequest> {
        return bosituasjoner.map { it.toService() }.sequence()
            .mapLeft { it }
            .map {
                LeggTilBosituasjonerRequest(
                    revurderingId = revurderingId,
                    bosituasjoner = it,
                )
            }
    }
}

private data class JsonBody(
    val periode: PeriodeJson,
    val epsFnr: String?,
    val delerBolig: Boolean?,
    val erEPSUførFlyktning: Boolean?,
    val begrunnelse: String?,
) {
    fun toService(): Either<Resultat, LeggTilBosituasjonRequest> {
        val periode = periode.toPeriodeOrResultat()
            .getOrHandle { return it.left() }

        return LeggTilBosituasjonRequest(
            periode = periode,
            epsFnr = epsFnr,
            delerBolig = delerBolig,
            ektemakeEllerSamboerUførFlyktning = erEPSUførFlyktning,
            begrunnelse = begrunnelse,
        ).right()
    }
}

internal fun Route.LeggTilBosituasjonRevurderingRoute(
    revurderingService: RevurderingService,
) {
    authorize(Brukerrolle.Saksbehandler) {
        post("$revurderingPath/{revurderingId}/bosituasjongrunnlag") {
            call.withSakId { sakId ->
                call.withRevurderingId { revurderingId ->
                    call.withBody<JsonRequest> { json ->
                        call.svar(
                            json.toService(revurderingId)
                                .mapLeft { it }
                                .flatMap {
                                    revurderingService.leggTilBosituasjongrunnlag(it)
                                        .mapLeft { feil -> feil.tilResultat() }
                                        .map { respons ->
                                            call.sikkerlogg("Lagret bosituasjon for revudering $revurderingId på $sakId")
                                            Resultat.json(
                                                HttpStatusCode.OK,
                                                serialize(respons.toJson()),
                                            )
                                        }
                                }.getOrHandle { it },
                        )
                    }
                }
            }
        }
    }
}

private fun KunneIkkeLeggeTilBosituasjongrunnlag.tilResultat() = when (this) {
    KunneIkkeLeggeTilBosituasjongrunnlag.FantIkkeBehandling -> {
        Revurderingsfeilresponser.fantIkkeRevurdering
    }
    KunneIkkeLeggeTilBosituasjongrunnlag.EpsAlderErNull -> {
        HttpStatusCode.InternalServerError.errorJson(
            "eps alder er null",
            "eps_alder_er_null",
        )
    }
    KunneIkkeLeggeTilBosituasjongrunnlag.KunneIkkeSlåOppEPS -> {
        HttpStatusCode.InternalServerError.errorJson(
            "kunne ikke slå opp EPS",
            "kunne_ikke_slå_opp_eps",
        )
    }
    KunneIkkeLeggeTilBosituasjongrunnlag.UgyldigData -> {
        ugyldigBody
    }
    is KunneIkkeLeggeTilBosituasjongrunnlag.KunneIkkeLeggeTilBosituasjon -> {
        when (val inner = this.feil) {
            is Revurdering.KunneIkkeLeggeTilBosituasjon.Konsistenssjekk -> {
                inner.feil.tilResultat()
            }
            is Revurdering.KunneIkkeLeggeTilBosituasjon.KunneIkkeOppdatereFormue -> {
                when (val innerInner = inner.feil) {
                    is Revurdering.KunneIkkeLeggeTilFormue.Konsistenssjekk -> {
                        innerInner.feil.tilResultat()
                    }
                    is Revurdering.KunneIkkeLeggeTilFormue.UgyldigTilstand -> {
                        Feilresponser.ugyldigTilstand(innerInner.fra, innerInner.til)
                    }
                }
            }
            Revurdering.KunneIkkeLeggeTilBosituasjon.PerioderMangler -> {
                HttpStatusCode.BadRequest.errorJson(
                    message = "Bosituasjon mangler for hele eller deler av behandlingsperioden",
                    code = "bosituasjon_mangler_for_perioder",
                )
            }
            is Revurdering.KunneIkkeLeggeTilBosituasjon.UgyldigTilstand -> {
                Feilresponser.ugyldigTilstand(inner.fra, inner.til)
            }
            is Revurdering.KunneIkkeLeggeTilBosituasjon.Valideringsfeil -> {
                inner.feil.tilResultat()
            }
        }
    }
    is KunneIkkeLeggeTilBosituasjongrunnlag.Konsistenssjekk -> {
        this.feil.tilResultat()
    }
}
