package no.nav.su.se.bakover.web.routes.grunnlag

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.vilkår.bosituasjon.KunneIkkeLeggeTilBosituasjongrunnlag
import no.nav.su.se.bakover.domain.revurdering.vilkår.bosituasjon.LeggTilBosituasjonRequest
import no.nav.su.se.bakover.domain.revurdering.vilkår.bosituasjon.LeggTilBosituasjonerRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.bosituasjon.KunneIkkeLeggeTilBosituasjon
import no.nav.su.se.bakover.web.routes.periode.toPeriodeOrResultat
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.tilResultat
import java.util.UUID

data class LeggTilBosituasjonJsonRequest(
    val bosituasjoner: List<JsonBody>,
) {
    fun toService(behandlingId: UUID): Either<Resultat, LeggTilBosituasjonerRequest> {
        return either {
            LeggTilBosituasjonerRequest(
                behandlingId = behandlingId,
                bosituasjoner = bosituasjoner.map { it.toService().bind() },
            )
        }
    }

    data class JsonBody(
        val periode: PeriodeJson,
        val epsFnr: String?,
        val delerBolig: Boolean?,
        val erEPSUførFlyktning: Boolean?,
    ) {
        fun toService(): Either<Resultat, LeggTilBosituasjonRequest> {
            val periode = periode.toPeriodeOrResultat()
                .getOrElse { return it.left() }

            return LeggTilBosituasjonRequest(
                periode = periode,
                epsFnr = epsFnr,
                delerBolig = delerBolig,
                ektemakeEllerSamboerUførFlyktning = erEPSUførFlyktning,
            ).right()
        }
    }
}

internal fun SøknadsbehandlingService.KunneIkkeVilkårsvurdere.tilResultat(): Resultat = when (this) {
    SøknadsbehandlingService.KunneIkkeVilkårsvurdere.FantIkkeBehandling -> Feilresponser.fantIkkeBehandling
}

internal fun KunneIkkeLeggeTilGrunnlag.KunneIkkeOppdatereBosituasjon.tilResultat(): Resultat = when (this) {
    is KunneIkkeLeggeTilGrunnlag.KunneIkkeOppdatereBosituasjon.UgyldigTilstand -> Feilresponser.ugyldigTilstand(
        this.fra,
        this.til,
    )

    KunneIkkeLeggeTilGrunnlag.KunneIkkeOppdatereBosituasjon.GrunnlagetMåVæreInnenforBehandlingsperioden -> Feilresponser.utenforBehandlingsperioden
}

internal fun KunneIkkeLeggeTilBosituasjongrunnlag.tilResultat() = when (this) {
    KunneIkkeLeggeTilBosituasjongrunnlag.FantIkkeBehandling -> Feilresponser.fantIkkeBehandling

    KunneIkkeLeggeTilBosituasjongrunnlag.EpsAlderErNull -> HttpStatusCode.InternalServerError.errorJson(
        "eps alder er null",
        "eps_alder_er_null",
    )

    KunneIkkeLeggeTilBosituasjongrunnlag.KunneIkkeSlåOppEPS -> HttpStatusCode.InternalServerError.errorJson(
        "kunne ikke slå opp EPS",
        "kunne_ikke_slå_opp_eps",
    )

    KunneIkkeLeggeTilBosituasjongrunnlag.UgyldigData -> Feilresponser.ugyldigBody

    is KunneIkkeLeggeTilBosituasjongrunnlag.KunneIkkeLeggeTilBosituasjon -> when (val inner = this.feil) {
        is KunneIkkeLeggeTilBosituasjon.Konsistenssjekk -> inner.feil.tilResultat()

        is KunneIkkeLeggeTilBosituasjon.KunneIkkeOppdatereFormue -> when (val innerInner = inner.feil) {
            is Revurdering.KunneIkkeLeggeTilFormue.Konsistenssjekk -> innerInner.feil.tilResultat()

            is Revurdering.KunneIkkeLeggeTilFormue.UgyldigTilstand -> Feilresponser.ugyldigTilstand(
                innerInner.fra,
                innerInner.til,
            )
        }

        KunneIkkeLeggeTilBosituasjon.PerioderMangler -> HttpStatusCode.BadRequest.errorJson(
            message = "Bosituasjon mangler for hele eller deler av behandlingsperioden",
            code = "bosituasjon_mangler_for_perioder",
        )

        is KunneIkkeLeggeTilBosituasjon.UgyldigTilstand -> Feilresponser.ugyldigTilstand(inner.fra, inner.til)

        is KunneIkkeLeggeTilBosituasjon.Valideringsfeil -> inner.feil.tilResultat()
    }

    is KunneIkkeLeggeTilBosituasjongrunnlag.Konsistenssjekk -> this.feil.tilResultat()

    is KunneIkkeLeggeTilBosituasjongrunnlag.KunneIkkeLeggeTilGrunnlag -> this.feil.tilResultat()
}

internal fun KunneIkkeLeggeTilGrunnlag.tilResultat(): Resultat = when (this) {
    KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag.GrunnlagetMåVæreInnenforBehandlingsperioden -> Feilresponser.utenforBehandlingsperioden
    is KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag.IkkeLovÅLeggeTilFradragIDenneStatusen -> SøknadsbehandlingService.KunneIkkeLeggeTilFradragsgrunnlag.UgyldigTilstand(
        this.status,
        VilkårsvurdertSøknadsbehandling::class,
    ).tilResultat()

    is KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag.KunneIkkeEndreFradragsgrunnlag -> this.feil.tilResultat()
    is KunneIkkeLeggeTilGrunnlag.KunneIkkeOppdatereBosituasjon.UgyldigTilstand -> KunneIkkeLeggeTilGrunnlag.KunneIkkeOppdatereBosituasjon.UgyldigTilstand(
        this.fra,
        this.til,
    ).tilResultat()

    KunneIkkeLeggeTilGrunnlag.KunneIkkeOppdatereBosituasjon.GrunnlagetMåVæreInnenforBehandlingsperioden -> Feilresponser.utenforBehandlingsperioden
}
