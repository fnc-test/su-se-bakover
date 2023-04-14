package no.nav.su.se.bakover.domain.tidslinje

import arrow.core.NonEmptyList
import arrow.core.toNonEmptyListOrNull
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.application.CopyArgs
import no.nav.su.se.bakover.common.between
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.minAndMaxOf
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.Utbetalinger
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje.Companion.lagTidslinje
import java.time.Instant
import java.time.LocalDate
import java.util.LinkedList

/**
 * Merk en tidslinje kan ha hull.
 * @property periode Denne perioden vil strekke seg fra første til siste utbetalingsmåned. Merk at den kan ha hull, så funksjoner som gjeldendeForDato og krymp kan gi null.
 */
data class TidslinjeForUtbetalinger private constructor(
    private val tidslinjeperioder: NonEmptyList<UtbetalingslinjePåTidslinje>,
) : List<UtbetalingslinjePåTidslinje> by tidslinjeperioder {

    val periode = tidslinjeperioder.map { it.periode }.minAndMaxOf()

    fun gjeldendeForDato(dato: LocalDate): UtbetalingslinjePåTidslinje? {
        return tidslinjeperioder.firstOrNull { dato.between(it.periode) }
    }

    /**
     * Sjekker om denne tidslinjen er ekvivalent med [tidslinje].
     */
    fun ekvivalentMed(
        tidslinje: TidslinjeForUtbetalinger,
    ): Boolean {
        return this.tidslinjeperioder.size == tidslinje.tidslinjeperioder.size && this.tidslinjeperioder.zip(
            tidslinje.tidslinjeperioder,
        ).all {
            it.first.ekvivalentMed(it.second)
        }
    }

    /**
     * Ekvivalens sjekkes bare hvis begge tidslinjene har et forhold til en angitte perioden.
     *
     * Selv om tidslinjene er ekvivalente, men perioden er utenfor tidslinjene, så vil vi uansett gi false
     */
    fun ekvivalentMedInnenforPeriode(
        tidslinje: TidslinjeForUtbetalinger,
        periode: Periode,
    ): Boolean {
        return krympTilPeriode(periode)
            ?.let { denneTidslinjen ->
                tidslinje.krympTilPeriode(periode)
                    ?.let { tidslinjeSomSjekkesMot -> denneTidslinjen.ekvivalentMed(tidslinjeSomSjekkesMot) }
            } ?: false
    }

    /**
     * En variant av 'copy' som kopierer innholdet i tidslinjen, men krymper på perioden
     * @return Dersom perioden som sendes inn ikke finnes i tidslinjen, så null
     */
    fun krympTilPeriode(
        periodenDetSkalKrympesTil: Periode,
    ): TidslinjeForUtbetalinger? {
        return this.tidslinjeperioder.mapNotNull {
            if (periodenDetSkalKrympesTil inneholder it.periode) {
                it
            } else if (periodenDetSkalKrympesTil overlapper it.periode) {
                it.copy(CopyArgs.Tidslinje.NyPeriode(periodenDetSkalKrympesTil.snitt(it.periode)!!))
            } else {
                null
            }
        }.let {
            it.toNonEmptyListOrNull()?.let {
                TidslinjeForUtbetalinger(it)
            }
        }
    }

    /**
     * [krympTilPeriode]
     */
    fun krympTilPeriode(
        fraOgMed: LocalDate,
    ): TidslinjeForUtbetalinger? = krympTilPeriode(Periode.create(fraOgMed, periode.tilOgMed))

    object RegenerertInformasjonVilOverskriveOriginaleOpplysningerSomErFerskereException : RuntimeException()

    companion object {
        fun fra(
            utbetalinger: Utbetalinger,
        ): TidslinjeForUtbetalinger? {
            return utbetalinger.flatMap { it.utbetalingslinjer }.toNonEmptyListOrNull()?.let {
                TidslinjeForUtbetalinger(
                    tidslinjeperioder = lagTidslinje(it).toNonEmptyList(),
                )
            }
        }

        operator fun invoke(
            utbetalingslinjer: NonEmptyList<Utbetalingslinje>,
        ): TidslinjeForUtbetalinger {
            return TidslinjeForUtbetalinger(
                tidslinjeperioder = lagTidslinje(utbetalingslinjer).toNonEmptyList(),
            )
        }

        private fun lagTidslinje(
            utbetalingslinjer: NonEmptyList<Utbetalingslinje>,
        ): Tidslinje<UtbetalingslinjePåTidslinje> {
            val nyesteFørst = Comparator<UtbetalingslinjePåTidslinje> { o1, o2 ->
                o2.opprettet.instant.compareTo(o1.opprettet.instant)
            }

            val nyeUtbetalingslinjer = utbetalingslinjer
                .map { it.mapTilTidslinje() }
                .filterIsInstance<UtbetalingslinjePåTidslinje.Ny>()

            val andreUtbetalingslinjer = utbetalingslinjer
                .map { it.mapTilTidslinje() }
                .filterNot { it is UtbetalingslinjePåTidslinje.Ny }

            val tidslinjeForNyeUtbetalinger = nyeUtbetalingslinjer.lagTidslinje() ?: emptyList()

            val utbetalingslinjerForTidslinje = tidslinjeForNyeUtbetalinger.union(andreUtbetalingslinjer)
                .sortedWith(nyesteFørst)

            val queue = LinkedList(utbetalingslinjerForTidslinje)

            val result = mutableListOf<UtbetalingslinjePåTidslinje>()

            while (queue.isNotEmpty()) {
                val last = queue.poll()
                if (queue.isNotEmpty()) {
                    when (last) {
                        /**
                         *  Ved reaktivering av utbetalingslinjer må vi generere nye data som gjenspeiler utbetalingene
                         *  som reaktiveres. Instanser av [UtbetalingslinjePåTidslinje.Reaktivering] bærer bare med seg
                         *  informasjon om den siste utbetalingslinjen på reaktiveringstidspunktet og følgelig må alle
                         *  [UtbetalingslinjePåTidslinje.Ny] overlappende med den totale "reaktiveringsperioden" regenereres
                         *  slik at informasjon fra disse "blir synlig".
                         */
                        is UtbetalingslinjePåTidslinje.Reaktivering -> {
                            result.add(last)
                            result.addAll(
                                queue.subList(
                                    0,
                                    queue.size,
                                )
                                    .filterIsInstance<UtbetalingslinjePåTidslinje.Ny>()
                                    .filter { it.periode overlapper last.periode && it.beløp != last.beløp }
                                    .map { utbetalingslinje ->
                                        /**
                                         * Setter nytt tidspunkt til å være marginalt ferskere enn reaktiveringen
                                         * slik at regenerert informasjon får høyere presedens ved opprettelse av
                                         * [Tidslinje]. Velger minste mulige enhet av [Tidspunkt] for å unngå
                                         * (så langt det lar seg gjøre) at regenerert informasjon får høyere presedens
                                         * enn informasjon som i utgangspunktet er ferskere enn selve reaktiveringen.
                                         * Kaster [RegenerertInformasjonVilOverskriveOriginaleOpplysningerSomErFerskereException]
                                         * dersom det likevel skulle vise seg at regenerert informasjon vil interferere
                                         * med original informasjon som er ferskere.
                                         */
                                        val periodeForUtbetalingslinje = Periode.create(
                                            maxOf(
                                                utbetalingslinje.periode.fraOgMed,
                                                last.periode.fraOgMed,
                                            ),
                                            minOf(
                                                utbetalingslinje.periode.tilOgMed,
                                                last.periode.tilOgMed,
                                            ),
                                        )
                                        val opprettet = last.opprettet.plusUnits(1)

                                        if (utbetalingslinjerForTidslinje.harOverlappendeMedOpprettetITidsintervall(
                                                fra = last.opprettet.instant,
                                                tilOgMed = opprettet.instant,
                                                periode = periodeForUtbetalingslinje,
                                            )
                                        ) {
                                            throw RegenerertInformasjonVilOverskriveOriginaleOpplysningerSomErFerskereException
                                        }

                                        UtbetalingslinjePåTidslinje.Reaktivering(
                                            kopiertFraId = utbetalingslinje.kopiertFraId,
                                            opprettet = opprettet,
                                            periode = periodeForUtbetalingslinje,
                                            beløp = utbetalingslinje.beløp,
                                        )
                                    },
                            )
                        }

                        else -> result.add(last)
                    }
                } else {
                    result.add(last)
                }
            }

            return result.lagTidslinje()!!
        }

        private fun List<UtbetalingslinjePåTidslinje>.harOverlappendeMedOpprettetITidsintervall(
            fra: Instant,
            tilOgMed: Instant,
            periode: Periode,
        ): Boolean {
            return this.any {
                it.periode overlapper periode &&
                    it.opprettet.instant > fra &&
                    it.opprettet.instant <= tilOgMed
            }
        }
    }
}

/**
 *  Mapper utbetalingslinjer til objekter som kan plasseres på tidslinjen.
 *  For subtyper av [no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje.Endring] erstattes
 *  [no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje.Endring.fraOgMed] med
 *  [no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje.Endring.virkningsperiode] da dette gjenspeiler datoen
 *  endringen effektueres hos oppdrag.
 *
 *  For typer som i praksis fører til at ingen ytelse utbetales, settes beløpet til 0.
 */
private fun Utbetalingslinje.mapTilTidslinje(): UtbetalingslinjePåTidslinje {
    return when (this) {
        is Utbetalingslinje.Endring.Opphør -> UtbetalingslinjePåTidslinje.Opphør(
            kopiertFraId = id,
            opprettet = opprettet,
            periode = periode,
        )

        is Utbetalingslinje.Endring.Reaktivering -> UtbetalingslinjePåTidslinje.Reaktivering(
            kopiertFraId = id,
            opprettet = opprettet,
            periode = periode,
            beløp = beløp,
        )

        is Utbetalingslinje.Endring.Stans -> UtbetalingslinjePåTidslinje.Stans(
            kopiertFraId = id,
            opprettet = opprettet,
            periode = periode,
        )

        is Utbetalingslinje.Ny -> UtbetalingslinjePåTidslinje.Ny(
            kopiertFraId = id,
            opprettet = opprettet,
            periode = periode,
            beløp = beløp,
        )
    }
}
