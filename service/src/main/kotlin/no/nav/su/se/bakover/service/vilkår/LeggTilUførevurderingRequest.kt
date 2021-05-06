package no.nav.su.se.bakover.service.vilkår

import arrow.core.Either
import arrow.core.Nel
import arrow.core.flatMap
import arrow.core.left
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import java.util.UUID

data class LeggTilUførevurderingRequest(
    val behandlingId: UUID,
    val periode: Periode,
    val uføregrad: Uføregrad?,
    val forventetInntekt: Int?,
    val oppfylt: Behandlingsinformasjon.Uførhet.Status,
    val begrunnelse: String?,
) {
    sealed class UgyldigUførevurdering {
        object UføregradOgForventetInntektMangler : UgyldigUførevurdering()
        object PeriodeForGrunnlagOgVurderingErForskjellig : UgyldigUførevurdering()
        object OverlappendeVurderingsperioder : UgyldigUførevurdering()
        object VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden : UgyldigUførevurdering()
    }

    /**
     * @param behandlingsperiode Ved en søknadsbehandling kan det være støndadsperiode. Ved en revurdering kan det være revurderingsperioden.
     */
    fun toVurderingsperiode(behandlingsperiode: Periode): Either<UgyldigUførevurdering, Vurderingsperiode<Grunnlag.Uføregrunnlag>> {
        if (!(behandlingsperiode inneholder periode)) return UgyldigUførevurdering.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden.left()
        return when (oppfylt) {
            Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt -> {
                if (uføregrad == null || forventetInntekt == null) return UgyldigUførevurdering.UføregradOgForventetInntektMangler.left()
                Vurderingsperiode.Manuell.tryCreate(
                    resultat = Resultat.Innvilget,
                    grunnlag = Grunnlag.Uføregrunnlag(
                        periode = periode,
                        uføregrad = uføregrad,
                        forventetInntekt = forventetInntekt,
                    ),
                    vurderingsperiode = periode,
                    begrunnelse = begrunnelse,
                )
            }
            Behandlingsinformasjon.Uførhet.Status.VilkårIkkeOppfylt -> Vurderingsperiode.Manuell.tryCreate(
                resultat = Resultat.Avslag,
                grunnlag = if (uføregrad == null || forventetInntekt == null) null else {
                    Grunnlag.Uføregrunnlag(
                        periode = periode,
                        uføregrad = uføregrad,
                        forventetInntekt = forventetInntekt,
                    )
                },
                vurderingsperiode = periode,
                begrunnelse = begrunnelse,
            )
            Behandlingsinformasjon.Uførhet.Status.HarUføresakTilBehandling -> Vurderingsperiode.Manuell.tryCreate(
                resultat = Resultat.Uavklart,
                grunnlag = if (uføregrad == null || forventetInntekt == null) null else {
                    Grunnlag.Uføregrunnlag(
                        periode = periode,
                        uføregrad = uføregrad,
                        forventetInntekt = forventetInntekt,
                    )
                },
                vurderingsperiode = periode,
                begrunnelse = begrunnelse,
            )
        }.mapLeft {
            when (it) {
                Vurderingsperiode.Manuell.UgyldigVurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig -> UgyldigUførevurdering.PeriodeForGrunnlagOgVurderingErForskjellig
            }
        }
    }

    fun toVilkår(behandlingsperiode: Periode): Either<UgyldigUførevurdering, Vilkår.Vurdert.Uførhet> {
        return toVurderingsperiode(behandlingsperiode)
            .flatMap { vurderingsperiode ->
                Vilkår.Vurdert.Uførhet.tryCreate(Nel.of(vurderingsperiode))
                    .mapLeft {
                        when (it) {
                            Vilkår.Vurdert.Uførhet.UgyldigUførevilkår.OverlappendeVurderingsperioder -> UgyldigUførevurdering.OverlappendeVurderingsperioder
                        }
                    }
            }
    }
}
