package no.nav.su.se.bakover.web.routes.vilkår

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.grunnlag.PersonligOppmøteGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.PersonligOppmøteÅrsak
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodePersonligOppmøte
import no.nav.su.se.bakover.domain.vilkår.oppmøte.KunneIkkeLeggeTilPersonligOppmøteVilkår
import java.time.Clock
import java.util.UUID

internal fun List<LeggTilVurderingsperiodePersonligOppmøteJson>.toDomain(clock: Clock): PersonligOppmøteVilkår.Vurdert {
    return map { it.toDomain(clock) }.let {
        PersonligOppmøteVilkår.Vurdert(
            it.toNonEmptyList(),
        )
    }
}

internal fun KunneIkkeLeggeTilPersonligOppmøteVilkår.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeLeggeTilPersonligOppmøteVilkår.FantIkkeBehandling -> {
            Feilresponser.fantIkkeBehandling
        }
        is KunneIkkeLeggeTilPersonligOppmøteVilkår.Revurdering -> {
            when (val feil = this.feil) {
                Revurdering.KunneIkkeLeggeTilPersonligOppmøteVilkår.HeleBehandlingsperiodenErIkkeVurdert -> {
                    Feilresponser.heleBehandlingsperiodenMåHaVurderinger
                }
                is Revurdering.KunneIkkeLeggeTilPersonligOppmøteVilkår.UgyldigTilstand -> {
                    Feilresponser.ugyldigTilstand(feil.fra, feil.til)
                }
            }
        }
        is KunneIkkeLeggeTilPersonligOppmøteVilkår.Søknadsbehandling -> {
            when (val feil = this.feil) {
                is KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilPersonligOppmøteVilkår.UgyldigTilstand -> {
                    Feilresponser.ugyldigTilstand(feil.fra, feil.til)
                }
            }
        }
    }
}

internal data class LeggTilVurderingsperiodePersonligOppmøteJson(
    val periode: PeriodeJson,
    val vurdering: PersonligOppmøteÅrsakJson,
) {
    fun toDomain(clock: Clock): VurderingsperiodePersonligOppmøte {
        val opprettet = Tidspunkt.now(clock)
        return VurderingsperiodePersonligOppmøte(
            id = UUID.randomUUID(),
            opprettet = opprettet,
            periode = periode.toPeriode(),
            grunnlag = PersonligOppmøteGrunnlag(
                id = UUID.randomUUID(),
                opprettet = opprettet,
                periode = periode.toPeriode(),
                årsak = vurdering.toDomain(),
            ),
        )
    }
}

enum class PersonligOppmøteÅrsakJson {
    MøttPersonlig,
    IkkeMøttMenVerge,
    IkkeMøttMenSykMedLegeerklæringOgFullmakt,
    IkkeMøttMenKortvarigSykMedLegeerklæring,
    IkkeMøttMenMidlertidigUnntakFraOppmøteplikt,
    IkkeMøttPersonlig,
    Uavklart,
    ;

    fun toDomain(): PersonligOppmøteÅrsak {
        return when (this) {
            MøttPersonlig -> PersonligOppmøteÅrsak.MøttPersonlig
            IkkeMøttMenVerge -> PersonligOppmøteÅrsak.IkkeMøttMenVerge
            IkkeMøttMenSykMedLegeerklæringOgFullmakt -> PersonligOppmøteÅrsak.IkkeMøttMenSykMedLegeerklæringOgFullmakt
            IkkeMøttMenKortvarigSykMedLegeerklæring -> PersonligOppmøteÅrsak.IkkeMøttMenKortvarigSykMedLegeerklæring
            IkkeMøttMenMidlertidigUnntakFraOppmøteplikt -> PersonligOppmøteÅrsak.IkkeMøttMenMidlertidigUnntakFraOppmøteplikt
            IkkeMøttPersonlig -> PersonligOppmøteÅrsak.IkkeMøttPersonlig
            Uavklart -> PersonligOppmøteÅrsak.Uavklart
        }
    }
}

private fun PersonligOppmøteÅrsak.toJson(): String {
    return when (this) {
        PersonligOppmøteÅrsak.IkkeMøttMenKortvarigSykMedLegeerklæring -> PersonligOppmøteÅrsakJson.IkkeMøttMenKortvarigSykMedLegeerklæring
        PersonligOppmøteÅrsak.IkkeMøttMenMidlertidigUnntakFraOppmøteplikt -> PersonligOppmøteÅrsakJson.IkkeMøttMenMidlertidigUnntakFraOppmøteplikt
        PersonligOppmøteÅrsak.IkkeMøttMenSykMedLegeerklæringOgFullmakt -> PersonligOppmøteÅrsakJson.IkkeMøttMenSykMedLegeerklæringOgFullmakt
        PersonligOppmøteÅrsak.IkkeMøttMenVerge -> PersonligOppmøteÅrsakJson.IkkeMøttMenVerge
        PersonligOppmøteÅrsak.IkkeMøttPersonlig -> PersonligOppmøteÅrsakJson.IkkeMøttPersonlig
        PersonligOppmøteÅrsak.MøttPersonlig -> PersonligOppmøteÅrsakJson.MøttPersonlig
        PersonligOppmøteÅrsak.Uavklart -> PersonligOppmøteÅrsakJson.Uavklart
    }.toString()
}

internal data class PersonligOppmøteVilkårJson(
    val vurderinger: List<VurderingsperiodePersonligOppmøteJson>,
    val resultat: String,
)

internal data class VurderingsperiodePersonligOppmøteJson(
    val resultat: String,
    val vurdering: String,
    val periode: PeriodeJson,
)

internal fun PersonligOppmøteVilkår.toJson(): PersonligOppmøteVilkårJson? {
    return when (this) {
        PersonligOppmøteVilkår.IkkeVurdert -> {
            null
        }
        is PersonligOppmøteVilkår.Vurdert -> {
            this.toJson()
        }
    }
}

internal fun PersonligOppmøteVilkår.Vurdert.toJson(): PersonligOppmøteVilkårJson {
    return PersonligOppmøteVilkårJson(
        vurderinger = vurderingsperioder.map { it.toJson() },
        resultat = vurdering.toJson(),
    )
}

internal fun VurderingsperiodePersonligOppmøte.toJson(): VurderingsperiodePersonligOppmøteJson {
    return VurderingsperiodePersonligOppmøteJson(
        resultat = vurdering.toJson(),
        vurdering = grunnlag.årsak.toJson(),
        periode = periode.toJson(),
    )
}

private fun Vurdering.toJson(): String {
    return when (this) {
        Vurdering.Avslag -> "VilkårIkkeOppfylt"
        Vurdering.Innvilget -> "VilkårOppfylt"
        Vurdering.Uavklart -> "Uavklart"
    }
}
