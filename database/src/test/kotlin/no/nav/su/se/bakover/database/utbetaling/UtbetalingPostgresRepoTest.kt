package no.nav.su.se.bakover.database.utbetaling

import io.kotest.matchers.shouldBe
import kotliquery.using
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.sessionOf
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class UtbetalingPostgresRepoTest {

    private val FNR = FnrGenerator.random()
    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val repo = UtbetalingPostgresRepo(EmbeddedDatabase.instance())

    @Test
    fun `hent utbetaling`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val utbetaling = testDataHelper.insertUtbetaling(sak.oppdrag.id, defaultUtbetaling())
            val hentet = repo.hentUtbetaling(utbetaling.id)

            utbetaling shouldBe hentet
        }
    }

    @Test
    fun `oppdater med kvittering`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val utbetaling = testDataHelper.insertUtbetaling(sak.oppdrag.id, defaultUtbetaling())
            val kvittering = Kvittering(
                Kvittering.Utbetalingsstatus.OK,
                "some xml",
                mottattTidspunkt = Tidspunkt.EPOCH
            )
            val oppdatert = repo.oppdaterMedKvittering(utbetaling.id, kvittering)

            oppdatert shouldBe utbetaling.copy(
                kvittering = kvittering
            )
        }
    }

    @Test
    fun `opprett og hent utbetalingslinjer`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val utbetaling = repo.opprettUtbetaling(sak.oppdrag.id, defaultUtbetaling())
            val utbetalingslinje1 = repo.opprettUtbetalingslinje(utbetaling.id, defaultUtbetalingslinje())
            val utbetalingslinje2 =
                repo.opprettUtbetalingslinje(utbetaling.id, defaultUtbetalingslinje(utbetalingslinje1.id))
            using(sessionOf(EmbeddedDatabase.instance())) {
                val hentet = UtbetalingInternalRepo.hentUtbetalingslinjer(utbetaling.id, it)
                listOf(utbetalingslinje1, utbetalingslinje2) shouldBe hentet
            }
        }
    }

    @Test
    fun `legg til og hent simulering`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val utbetaling = repo.opprettUtbetaling(sak.oppdrag.id, defaultUtbetaling())
            val simulering = Simulering(
                gjelderId = Fnr("12345678910"),
                gjelderNavn = "gjelderNavn",
                datoBeregnet = LocalDate.now(),
                nettoBeløp = 1,
                periodeList = listOf(
                    SimulertPeriode(
                        fom = LocalDate.now(),
                        tom = LocalDate.now(),
                        utbetaling = listOf(
                            SimulertUtbetaling(
                                fagSystemId = "fagSystemId",
                                utbetalesTilId = Fnr("12345678910"),
                                utbetalesTilNavn = "utbetalesTilNavn",
                                forfall = LocalDate.now(),
                                feilkonto = false,
                                detaljer = listOf(
                                    SimulertDetaljer(
                                        faktiskFom = LocalDate.now(),
                                        faktiskTom = LocalDate.now(),
                                        konto = "konto",
                                        belop = 1,
                                        tilbakeforing = true,
                                        sats = 1,
                                        typeSats = "",
                                        antallSats = 1,
                                        uforegrad = 2,
                                        klassekode = "klassekode",
                                        klassekodeBeskrivelse = "klassekodeBeskrivelse",
                                        klasseType = KlasseType.YTEL
                                    )
                                )
                            )
                        )
                    )
                )
            ).also {
                repo.addSimulering(
                    utbetalingId = utbetaling.id,
                    simulering = it
                )
            }
            val hentet = repo.hentUtbetaling(utbetaling.id)!!.getSimulering()!!
            simulering shouldBe hentet
        }
    }

    // @Test //TODO add when migrated
    // fun `beskytter mot sletting av utbetalinger som ikke skal slettes`() {
    //     withMigratedDb {
    //         val sak = insertSak(FNR)
    //         val utbetaling = insertUtbetaling(sak.oppdrag.id, Kvittering(Kvittering.Utbetalingsstatus.OK, ""))
    //         val utbetalingslinje1 = insertUtbetalingslinje(utbetaling.id, null)
    //         val utbetalingslinje2 = insertUtbetalingslinje(utbetaling.id, utbetalingslinje1.forrigeUtbetalingslinjeId)
    //         utbetaling.addOppdragsmelding(Oppdragsmelding(Oppdragsmelding.Oppdragsmeldingstatus.SENDT, ""))
    //
    //         assertThrows<IllegalStateException> { repo.slettUtbetaling(utbetaling) }
    //
    //         val skulleIkkeSlettes = utbetalingRepo.hentUtbetaling(utbetaling.id)
    //         skulleIkkeSlettes!!.id shouldBe utbetaling.id
    //         skulleIkkeSlettes.utbetalingslinjer shouldBe listOf(utbetalingslinje1, utbetalingslinje2)
    //     }
    // }

    private fun defaultUtbetaling() = Utbetaling(
        id = UUID30.randomUUID(),
        utbetalingslinjer = listOf(),
        fnr = FNR
    )

    private fun defaultUtbetalingslinje(forrigeUtbetalingslinjeId: UUID30? = null) = Utbetalingslinje(
        fom = 1.januar(2020),
        tom = 31.desember(2020),
        forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
        beløp = 25000
    )
}
