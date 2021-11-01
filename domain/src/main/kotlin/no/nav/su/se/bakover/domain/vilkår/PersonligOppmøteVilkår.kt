package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.overlappende
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.PersonligOppmøteGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

sealed class PersonligOppmøteVilkår : Vilkår() {
    override val vilkår = Inngangsvilkår.PersonligOppmøte
    abstract val grunnlag: List<PersonligOppmøteGrunnlag>

    abstract override fun lagTidslinje(periode: Periode): PersonligOppmøteVilkår
    abstract override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): PersonligOppmøteVilkår

    companion object {
        fun tryCreate(
            periode: Periode,
            personligOppmøte: Behandlingsinformasjon.PersonligOppmøte,
            clock: Clock,
        ): PersonligOppmøteVilkår {
            return when (personligOppmøte.status) {
                Behandlingsinformasjon.PersonligOppmøte.Status.MøttPersonlig,
                Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttMenVerge,
                Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttMenSykMedLegeerklæringOgFullmakt,
                Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttMenKortvarigSykMedLegeerklæring,
                Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttMenMidlertidigUnntakFraOppmøteplikt,
                Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttPersonlig,
                -> {
                    Vurdert.tryCreate(
                        vurderingsperioder = nonEmptyListOf(
                            VurderingsperiodePersonligOppmøte.tryCreate(
                                id = UUID.randomUUID(),
                                opprettet = Tidspunkt.now(clock),
                                resultat = when (personligOppmøte.erVilkårOppfylt()) {
                                    true -> Resultat.Innvilget
                                    false -> Resultat.Avslag
                                },
                                grunnlag = PersonligOppmøteGrunnlag.tryCreate(
                                    id = UUID.randomUUID(),
                                    opprettet = Tidspunkt.now(clock),
                                    periode = periode,
                                ).getOrHandle {
                                    throw IllegalArgumentException("Kunne ikke instansiere ${PersonligOppmøteGrunnlag::class.simpleName}. Melding: $it")
                                },
                                vurderingsperiode = periode,
                                begrunnelse = personligOppmøte.begrunnelse ?: "",
                            ).getOrHandle {
                                throw IllegalArgumentException("Kunne ikke instansiere ${VurderingsperiodePersonligOppmøte::class.simpleName}. Melding: $it")
                            },
                        ),
                    ).getOrHandle {
                        throw IllegalArgumentException("Kunne ikke instansiere ${Vurdert::class.simpleName}. Melding: $it")
                    }
                }
                Behandlingsinformasjon.PersonligOppmøte.Status.Uavklart -> IkkeVurdert
            }
        }
    }

    object IkkeVurdert : PersonligOppmøteVilkår() {
        override val resultat: Resultat = Resultat.Uavklart
        override val erAvslag = false
        override val erInnvilget = false
        override val grunnlag = emptyList<PersonligOppmøteGrunnlag>()
        override fun lagTidslinje(periode: Periode): PersonligOppmøteVilkår {
            return this
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): IkkeVurdert = this
        override fun hentTidligesteDatoForAvslag(): LocalDate? = null
        override fun erLik(other: Vilkår): Boolean {
            return other is IkkeVurdert
        }
    }

    data class Vurdert private constructor(
        val vurderingsperioder: Nel<VurderingsperiodePersonligOppmøte>,
    ) : PersonligOppmøteVilkår() {

        override val grunnlag: List<PersonligOppmøteGrunnlag> = vurderingsperioder.mapNotNull { it.grunnlag }
        override fun lagTidslinje(periode: Periode): PersonligOppmøteVilkår {
            return copy(
                vurderingsperioder = Nel.fromListUnsafe(
                    Tidslinje(
                        periode = periode,
                        objekter = vurderingsperioder,
                    ).tidslinje,
                ),
            )
        }

        override val erInnvilget: Boolean = vurderingsperioder.all { it.resultat == Resultat.Innvilget }

        override val erAvslag: Boolean = vurderingsperioder.any { it.resultat == Resultat.Avslag }

        override val resultat: Resultat =
            if (erInnvilget) Resultat.Innvilget else if (erAvslag) Resultat.Avslag else Resultat.Uavklart

        override fun hentTidligesteDatoForAvslag(): LocalDate? {
            return vurderingsperioder
                .filter { it.resultat == Resultat.Avslag }
                .map { it.periode.fraOgMed }
                .minByOrNull { it }
        }

        override fun erLik(other: Vilkår): Boolean {
            return other is Vurdert && vurderingsperioder.erLik(other.vurderingsperioder)
        }

        companion object {
            fun tryCreate(
                vurderingsperioder: Nel<VurderingsperiodePersonligOppmøte>,
            ): Either<UgyldigPersonligOppmøteVilkår, Vurdert> {
                if (vurderingsperioder.overlappende()) {
                    return UgyldigPersonligOppmøteVilkår.OverlappendeVurderingsperioder.left()
                }
                return Vurdert(vurderingsperioder).right()
            }
        }

        sealed class UgyldigPersonligOppmøteVilkår {
            object OverlappendeVurderingsperioder : UgyldigPersonligOppmøteVilkår()
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): PersonligOppmøteVilkår {
            check(vurderingsperioder.count() == 1) { "Kan ikke oppdatere stønadsperiode for vilkår med med enn èn vurdering" }
            return copy(
                vurderingsperioder = vurderingsperioder.map {
                    it.oppdaterStønadsperiode(stønadsperiode)
                },
            )
        }
    }
}

data class VurderingsperiodePersonligOppmøte private constructor(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val resultat: Resultat,
    override val grunnlag: PersonligOppmøteGrunnlag?,
    override val periode: Periode,
    val begrunnelse: String?,
) : Vurderingsperiode(), KanPlasseresPåTidslinje<VurderingsperiodePersonligOppmøte> {

    override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): VurderingsperiodePersonligOppmøte {
        return create(
            id = id,
            opprettet = opprettet,
            resultat = resultat,
            periode = stønadsperiode.periode,
            grunnlag = this.grunnlag?.oppdaterPeriode(stønadsperiode.periode),
            begrunnelse = begrunnelse,
        )
    }

    override fun copy(args: CopyArgs.Tidslinje): VurderingsperiodePersonligOppmøte = when (args) {
        CopyArgs.Tidslinje.Full -> {
            copy(
                id = UUID.randomUUID(),
                grunnlag = grunnlag?.copy(args),
            )
        }
        is CopyArgs.Tidslinje.NyPeriode -> {
            copy(
                id = UUID.randomUUID(),
                periode = args.periode,
                grunnlag = grunnlag?.copy(args),
            )
        }
    }

    override fun erLik(other: Vurderingsperiode): Boolean {
        return other is VurderingsperiodePersonligOppmøte &&
            resultat == other.resultat &&
            when {
                grunnlag != null && other.grunnlag != null -> grunnlag.erLik(other.grunnlag)
                grunnlag == null && other.grunnlag == null -> true
                else -> false
            }
    }

    companion object {
        fun create(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            resultat: Resultat,
            grunnlag: PersonligOppmøteGrunnlag?,
            periode: Periode,
            begrunnelse: String?,
        ): VurderingsperiodePersonligOppmøte {
            return tryCreate(id, opprettet, resultat, grunnlag, periode, begrunnelse).getOrHandle {
                throw IllegalArgumentException(it.toString())
            }
        }

        fun tryCreate(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            resultat: Resultat,
            grunnlag: PersonligOppmøteGrunnlag?,
            vurderingsperiode: Periode,
            begrunnelse: String?,
        ): Either<UgyldigVurderingsperiode, VurderingsperiodePersonligOppmøte> {

            grunnlag?.let {
                if (vurderingsperiode != it.periode) return UgyldigVurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig.left()
            }

            return VurderingsperiodePersonligOppmøte(
                id = id,
                opprettet = opprettet,
                resultat = resultat,
                grunnlag = grunnlag,
                periode = vurderingsperiode,
                begrunnelse = begrunnelse,
            ).right()
        }
    }

    sealed class UgyldigVurderingsperiode {
        object PeriodeForGrunnlagOgVurderingErForskjellig : UgyldigVurderingsperiode()
    }
}
