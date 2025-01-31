package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import arrow.core.NonEmptyList
import arrow.core.toNonEmptyListOrNull
import no.nav.su.se.bakover.common.extensions.between
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.minAndMaxOf
import no.nav.su.se.bakover.common.tid.periode.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.common.tid.periode.minus
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import java.time.LocalDate

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

    fun gjeldendeForMåned(måned: Måned): UtbetalingslinjePåTidslinje? {
        return tidslinjeperioder.firstOrNull { it.periode.inneholder(måned) }
    }

    /**
     * Sjekker om denne tidslinjen er ekvivalent med [other].
     * Ulik dersom antall linjer er ulik.
     * Lik dersom begge listene er tomme.
     */
    fun ekvivalentMed(
        other: TidslinjeForUtbetalinger,
    ): Boolean {
        return this.tidslinjeperioder.ekvivalentMed(other.tidslinjeperioder)
    }

    /**
     * Sjekker om denne tidslinjen er ekvivalent med [other].
     * Ulik dersom antall linjer er ulik.
     * Lik dersom begge listene er tomme.
     */
    fun ekvivalentMedInnenforPeriode(
        other: TidslinjeForUtbetalinger,
        periode: Periode,
    ): Boolean {
        return this.tidslinjeperioder.ekvivalentMedInnenforPeriode(other.tidslinjeperioder, periode)
    }

    /**
     * En variant av 'copy' som kopierer innholdet i tidslinjen, men krymper på perioden
     * @return Dersom perioden som sendes inn ikke finnes i tidslinjen, så null
     */
    fun krympTilPeriode(
        periodenDetSkalKrympesTil: Periode,
    ): TidslinjeForUtbetalinger? {
        return tidslinjeperioder.krympTilPeriode(periodenDetSkalKrympesTil).let {
            it.toNonEmptyListOrNull()?.let {
                TidslinjeForUtbetalinger(it)
            }
        }
    }

    fun krympTilPeriode(
        fraOgMed: LocalDate,
    ): TidslinjeForUtbetalinger? {
        if (fraOgMed.isAfter(periode.tilOgMed)) return null
        return krympTilPeriode(Periode.create(fraOgMed, periode.tilOgMed))
    }

    companion object {
        fun fra(
            utbetalinger: Utbetalinger,
        ): TidslinjeForUtbetalinger? {
            return utbetalinger.utbetalingslinjer.toNonEmptyListOrNull()?.tidslinje()
        }

        /**
         * Skal kun brukes i tilfeller hvor vi ikke har et komplett sett med utbetalinger.
         * F.eks. avstemming (hvor vi skal avstemme en gitt mengde utbetalinger).
         * // TODO jah: validering av utbetalinger (se init i Utbetalinger)
         */
        fun fra(
            utbetalinger: List<Utbetaling>,
        ): TidslinjeForUtbetalinger? {
            return utbetalinger.flatMap { it.utbetalingslinjer }.toNonEmptyListOrNull()?.tidslinje()
        }

        fun fra(
            utbetaling: Utbetaling,
        ): TidslinjeForUtbetalinger {
            return utbetaling.utbetalingslinjer.tidslinje()
        }

        private fun NonEmptyList<Utbetalingslinje>.tidslinje(): TidslinjeForUtbetalinger {
            val sortedBy = this
                .reversed()
                .fold(emptyList<UtbetalingslinjePåTidslinje>()) { acc, element ->
                    val inkluderElementer: List<UtbetalingslinjePåTidslinje> =
                        ((element.periode - acc.map { it.periode })).flatMap { nyPeriode ->
                            if (element is Utbetalingslinje.Endring.Reaktivering) {
                                this.hentNyLinjerForReaktivering(element, nyPeriode)
                            } else {
                                listOf(element.mapTilTidslinje(nyPeriode))
                            }
                        }
                    acc + inkluderElementer
                }.sortedBy { it.periode }
            return sortedBy
                .toNonEmptyList().let {
                    TidslinjeForUtbetalinger(it)
                }
        }

        private fun List<Utbetalingslinje>.hentNyLinjerForReaktivering(
            reaktivering: Utbetalingslinje.Endring.Reaktivering,
            nyPeriode: Periode,
        ): List<UtbetalingslinjePåTidslinje.Reaktivering> {
            return this
                .filterIsInstance<Utbetalingslinje.Ny>()
                .filter { it.periode.overlapper(nyPeriode) }
                .reversed()
                .fold<Utbetalingslinje.Ny, List<UtbetalingslinjePåTidslinje.Reaktivering>>(emptyList()) { acc, element ->
                    val snittPeriode: List<Periode> =
                        listOf(nyPeriode snitt element.periode).mapNotNull { it }

                    val inkluderElementer = (snittPeriode.minus(acc.map { it.periode })).map { nyPeriode ->
                        UtbetalingslinjePåTidslinje.Reaktivering(
                            kopiertFraId = element.id,
                            periode = nyPeriode,
                            beløp = element.beløp,
                        )
                    }
                    acc + inkluderElementer
                }.sortedBy { it.periode }.also {
                    require(
                        it.isNotEmpty() && it.map { it.periode }.minsteAntallSammenhengendePerioder()
                            .single() == nyPeriode,
                    ) {
                        "Tidslinje med reaktivering $reaktivering mangler NYe linjer for reaktiveringsperiode. Fant: $it"
                    }
                }
        }
    }

    init {
        tidslinjeperioder.map { it.periode }.zipWithNext { a, b ->
            require(a.før(b)) { "Tidslinje må være sortert etter periode og ikke overlappe, men $a er etter $b" }
        }
    }
}
