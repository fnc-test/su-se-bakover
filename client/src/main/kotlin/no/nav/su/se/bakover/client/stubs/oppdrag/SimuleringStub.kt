package no.nav.su.se.bakover.client.stubs.oppdrag

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.tidslinje.TidslinjeForUtbetalinger
import java.time.Clock
import java.time.LocalDate

class SimuleringStub(
    val clock: Clock,
    val utbetalingRepo: UtbetalingRepo,
) : SimuleringClient {

    override fun simulerUtbetaling(utbetaling: Utbetaling): Either<SimuleringFeilet, Simulering> {
        return simulerUtbetalinger(utbetaling).right()
    }

    private fun List<SimulertPeriode>.calculateNetto() =
        this.sumOf { it.bruttoYtelse() } + this.sumOf { simulertPeriode ->
            simulertPeriode.utbetaling
                .flatMap { it.detaljer }
                .filter { !it.isYtelse() }
                .sumOf { it.belop }
        }

    private fun simulerIngenUtbetaling(utbetaling: Utbetaling): Simulering {
        val simuleringsPeriode = when (val sisteUtbetalingslinje = utbetaling.sisteUtbetalingslinje()) {
            is Utbetalingslinje.Endring -> SimulertPeriode(
                fraOgMed = sisteUtbetalingslinje.virkningstidspunkt,
                tilOgMed = utbetaling.senesteDato(),
                utbetaling = emptyList(),
            )
            else -> SimulertPeriode(
                fraOgMed = utbetaling.tidligsteDato(),
                tilOgMed = utbetaling.senesteDato(),
                utbetaling = emptyList(),
            )
        }

        return Simulering(
            gjelderId = utbetaling.fnr,
            gjelderNavn = "MYGG LUR",
            datoBeregnet = idag(clock),
            nettoBeløp = 0,
            periodeList = listOf(simuleringsPeriode),
        )
    }

    private fun simulerUtbetalinger(utbetaling: Utbetaling): Simulering {
        return utbetaling.utbetalingslinjer.map {
            when (it) {
                is Utbetalingslinje.Endring -> {
                    Periode.create(it.virkningstidspunkt, it.tilOgMed)
                }
                is Utbetalingslinje.Ny -> {
                    Periode.create(it.fraOgMed, it.tilOgMed)
                }
            }
        }.let { perioder ->
            Periode.create(perioder.minOf { it.fraOgMed }, perioder.maxOf { it.tilOgMed })
        }.let { utbetalingsperiode ->
            val tidslinje = TidslinjeForUtbetalinger(
                periode = utbetalingsperiode,
                utbetalingslinjer = utbetalingRepo.hentUtbetalinger(utbetaling.sakId)
                    .flatMap { it.utbetalingslinjer },
                clock = clock,
            )

            utbetalingsperiode.tilMånedsperioder()
                .asSequence()
                .mapNotNull { måned ->
                    val utbetaltLinje = tidslinje.gjeldendeForDato(måned.fraOgMed)
                    val nyLinje = utbetaling.finnUtbetalingslinjeForDato(måned.fraOgMed)

                    when (utbetaling.type) {
                        Utbetaling.UtbetalingsType.NY -> {
                            if (utbetaltLinje != null && måned.tilOgMed < Tidspunkt.now(clock)
                                .toLocalDate(zoneIdOslo)
                            ) {
                                val feilutbetaling = nyLinje.beløp - utbetaltLinje.beløp < 0

                                måned to SimulertUtbetaling(
                                    fagSystemId = utbetaling.saksnummer.toString(),
                                    utbetalesTilId = utbetaling.fnr,
                                    utbetalesTilNavn = "LYR MYGG",
                                    forfall = LocalDate.now(clock),
                                    feilkonto = feilutbetaling,
                                    detaljer = listOf(
                                        createTidligereUtbetalt(
                                            fraOgMed = måned.fraOgMed,
                                            tilOgMed = måned.tilOgMed,
                                            beløp = utbetaltLinje.beløp,
                                        ),
                                        if (feilutbetaling) {
                                            createFeilutbetaling(
                                                fraOgMed = måned.fraOgMed,
                                                tilOgMed = måned.tilOgMed,
                                                beløp = utbetaltLinje.beløp - nyLinje.beløp,
                                            )
                                        } else {
                                            createOrdinær(
                                                fraOgMed = måned.fraOgMed,
                                                tilOgMed = måned.tilOgMed,
                                                beløp = nyLinje.beløp,
                                            )
                                        },
                                    ),
                                )
                            } else {
                                måned to SimulertUtbetaling(
                                    fagSystemId = utbetaling.saksnummer.toString(),
                                    utbetalesTilId = utbetaling.fnr,
                                    utbetalesTilNavn = "LYR MYGG",
                                    forfall = LocalDate.now(clock),
                                    feilkonto = false,
                                    detaljer = listOf(
                                        createOrdinær(
                                            fraOgMed = måned.fraOgMed,
                                            tilOgMed = måned.tilOgMed,
                                            beløp = nyLinje.beløp,
                                        ),
                                    ),
                                )
                            }
                        }
                        Utbetaling.UtbetalingsType.STANS -> {
                            null
                        }
                        Utbetaling.UtbetalingsType.GJENOPPTA -> {
                            måned to SimulertUtbetaling(
                                fagSystemId = utbetaling.saksnummer.toString(),
                                utbetalesTilId = utbetaling.fnr,
                                utbetalesTilNavn = "LYR MYGG",
                                forfall = LocalDate.now(clock),
                                feilkonto = false,
                                detaljer = listOf(
                                    createOrdinær(
                                        fraOgMed = måned.fraOgMed,
                                        tilOgMed = måned.tilOgMed,
                                        beløp = nyLinje.beløp,
                                    ),
                                ),
                            )
                        }
                        Utbetaling.UtbetalingsType.OPPHØR -> {
                            if (utbetaltLinje != null && måned.tilOgMed < Tidspunkt.now(clock)
                                .toLocalDate(zoneIdOslo)
                            ) {
                                måned to SimulertUtbetaling(
                                    fagSystemId = utbetaling.saksnummer.toString(),
                                    utbetalesTilId = utbetaling.fnr,
                                    utbetalesTilNavn = "LYR MYGG",
                                    forfall = LocalDate.now(clock),
                                    feilkonto = true,
                                    detaljer = listOf(
                                        createTidligereUtbetalt(
                                            måned.fraOgMed,
                                            måned.tilOgMed,
                                            utbetaltLinje.beløp,
                                        ),
                                        createFeilutbetaling(
                                            måned.fraOgMed,
                                            måned.tilOgMed,
                                            utbetaltLinje.beløp,
                                        ),
                                    ),
                                )
                            } else {
                                null
                            }
                        }
                    }
                }
                .map { (periode, simulertUtbetaling) ->
                    SimulertPeriode(
                        fraOgMed = periode.fraOgMed,
                        tilOgMed = periode.tilOgMed,
                        utbetaling = listOf(simulertUtbetaling),
                    )
                }
                .toList()
                .let {
                    Simulering(
                        gjelderId = utbetaling.fnr,
                        gjelderNavn = "MYGG LUR",
                        datoBeregnet = idag(clock),
                        nettoBeløp = it.calculateNetto(),
                        periodeList = it,
                    )
                }
        }
    }
}

private fun Utbetaling.finnUtbetalingslinjeForDato(fraOgMed: LocalDate): Utbetalingslinje {
    return utbetalingslinjer.first {
        when (it) {
            is Utbetalingslinje.Endring -> it.tilOgMed >= fraOgMed
            is Utbetalingslinje.Ny -> it.tilOgMed >= fraOgMed
        }
    }
}

private fun createOrdinær(fraOgMed: LocalDate, tilOgMed: LocalDate, beløp: Int) = SimulertDetaljer(
    faktiskFraOgMed = fraOgMed,
    faktiskTilOgMed = tilOgMed,
    konto = "4952000",
    belop = beløp,
    tilbakeforing = false,
    sats = beløp,
    typeSats = "MND",
    antallSats = 1,
    uforegrad = 0,
    klassekode = KlasseKode.SUUFORE,
    klassekodeBeskrivelse = "Supplerende stønad Uføre",
    klasseType = KlasseType.YTEL,
)

private fun createTidligereUtbetalt(fraOgMed: LocalDate, tilOgMed: LocalDate, beløp: Int) = SimulertDetaljer(
    faktiskFraOgMed = fraOgMed,
    faktiskTilOgMed = tilOgMed,
    konto = "4952000",
    belop = -beløp,
    tilbakeforing = true,
    sats = 0,
    typeSats = "",
    antallSats = 0,
    uforegrad = 0,
    klassekode = KlasseKode.SUUFORE,
    klassekodeBeskrivelse = "suBeskrivelse",
    klasseType = KlasseType.YTEL,
)

private fun createFeilutbetaling(fraOgMed: LocalDate, tilOgMed: LocalDate, beløp: Int) = SimulertDetaljer(
    faktiskFraOgMed = fraOgMed,
    faktiskTilOgMed = tilOgMed,
    konto = "4952000",
    belop = beløp,
    tilbakeforing = false,
    sats = 0,
    typeSats = "",
    antallSats = 0,
    uforegrad = 0,
    klassekode = KlasseKode.KL_KODE_FEIL_INNT,
    klassekodeBeskrivelse = "Feilutbetaling Inntektsytelser",
    klasseType = KlasseType.FEIL,
)
