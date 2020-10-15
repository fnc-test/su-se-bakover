package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag.UtbetalingStrategy.Gjenoppta
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingStrategyException
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class OppdragGjenopptaStrategyTest {

    private val fnr = Fnr("12345678910")

    @Test
    fun `gjenopptar enkel utbetaling`() {
        val opprinnelig = createOversendtUtbetaling(
            listOf(
                Utbetalingslinje(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 1500
                )
            ),
            type = Utbetaling.UtbetalingType.NY
        )

        val stans = createOversendtUtbetaling(
            listOf(
                Utbetalingslinje(
                    fraOgMed = 1.oktober(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = opprinnelig.utbetalingslinjer[0].id,
                    beløp = 0
                )
            ),
            type = Utbetaling.UtbetalingType.GJENOPPTA
        )

        createOppdrag(mutableListOf(opprinnelig, stans)).genererUtbetaling(
            strategy = Gjenoppta,
            fnr = fnr
        ).utbetalingslinjer[0].assert(
            fraOgMed = 1.oktober(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinje = stans.utbetalingslinjer[0].id,
            beløp = opprinnelig.utbetalingslinjer[0].beløp
        )
    }

    @Test
    fun `kan ikke gjenopprette dersom utbetalinger ikke er oversendt`() {
        assertThrows<UtbetalingStrategyException> {
            createOppdrag(mutableListOf()).genererUtbetaling(
                Gjenoppta,
                fnr
            )
        }.also {
            it.message shouldContain "Ingen oversendte utbetalinger"
        }
    }

    @Test
    fun `gjenopptar mer 'avansert' utbetaling`() {
        val første = createOversendtUtbetaling(
            listOf(
                Utbetalingslinje(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 1500
                )
            ),
            type = Utbetaling.UtbetalingType.NY

        )

        val førsteStans = createOversendtUtbetaling(
            listOf(
                element = Utbetalingslinje(
                    fraOgMed = 1.oktober(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = første.utbetalingslinjer[0].id,
                    beløp = 0
                )
            ),
            type = Utbetaling.UtbetalingType.STANS
        )

        val førsteGjenopptak = createOversendtUtbetaling(
            listOf(
                element = Utbetalingslinje(
                    fraOgMed = 1.oktober(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = førsteStans.utbetalingslinjer[0].id,
                    beløp = 1500
                )
            ),
            type = Utbetaling.UtbetalingType.GJENOPPTA
        )

        val andre = createOversendtUtbetaling(
            utbetalingslinjer = listOf(
                Utbetalingslinje(
                    fraOgMed = 1.november(2020),
                    tilOgMed = 31.oktober(2021),
                    forrigeUtbetalingslinjeId = førsteStans.utbetalingslinjer[0].id,
                    beløp = 5100
                )
            ),
            type = Utbetaling.UtbetalingType.NY
        )

        val andreStans = createOversendtUtbetaling(
            utbetalingslinjer = listOf(
                Utbetalingslinje(
                    fraOgMed = 1.mai(2021),
                    tilOgMed = 31.oktober(2021),
                    forrigeUtbetalingslinjeId = andre.utbetalingslinjer[0].id,
                    beløp = 0
                )
            ),
            type = Utbetaling.UtbetalingType.STANS
        )

        createOppdrag(mutableListOf(første, førsteStans, førsteGjenopptak, andre, andreStans)).genererUtbetaling(
            strategy = Gjenoppta,
            fnr = fnr
        ).utbetalingslinjer[0].assert(
            fraOgMed = 1.mai(2021),
            tilOgMed = 31.oktober(2021),
            forrigeUtbetalingslinje = andreStans.utbetalingslinjer[0].id,
            beløp = 5100
        )
    }

    @Test
    fun `kan ikke gjenoppta utbetalinger hvis ingen er stanset`() {
        val første = createOversendtUtbetaling(
            listOf(
                Utbetalingslinje(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 1500
                )
            ),
            type = Utbetaling.UtbetalingType.NY
        )

        assertThrows<UtbetalingStrategyException> {
            createOppdrag(mutableListOf(første)).genererUtbetaling(
                strategy = Gjenoppta,
                fnr = fnr
            )
        }.also {
            it.message shouldContain "Fant ingen utbetalinger som kan gjenopptas"
        }
    }

    @Test
    fun `gjenopptar utbetalinger med flere utbetalingslinjer`() {
        val l1 = Utbetalingslinje(
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1500
        )
        val l2 = Utbetalingslinje(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = l1.id,
            beløp = 5100
        )
        val første = createOversendtUtbetaling(
            listOf(l1, l2), Utbetaling.UtbetalingType.NY
        )

        val stans = createOversendtUtbetaling(
            utbetalingslinjer = listOf(
                Utbetalingslinje(
                    fraOgMed = 1.april(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = første.utbetalingslinjer[1].id,
                    beløp = 0
                )
            ),
            type = Utbetaling.UtbetalingType.STANS
        )

        createOppdrag(mutableListOf(første, stans)).genererUtbetaling(
            strategy = Gjenoppta,
            fnr = fnr
        ).also {
            it.utbetalingslinjer[0].assert(
                fraOgMed = 1.april(2020),
                tilOgMed = 30.april(2020),
                forrigeUtbetalingslinje = stans.utbetalingslinjer[0].id,
                beløp = 1500
            )
            it.utbetalingslinjer[1].assert(
                fraOgMed = 1.mai(2020),
                tilOgMed = 31.desember(2020),
                forrigeUtbetalingslinje = it.utbetalingslinjer[0].id,
                beløp = 5100
            )
        }
    }

    fun createOppdrag(utbetalinger: MutableList<Utbetaling>) = Oppdrag(
        id = UUID30.randomUUID(),
        opprettet = Tidspunkt.now(),
        sakId = UUID.randomUUID(),
        utbetalinger = utbetalinger
    )

    fun createOversendtUtbetaling(utbetalingslinjer: List<Utbetalingslinje>, type: Utbetaling.UtbetalingType) = Utbetaling.OversendtUtbetaling(
        oppdragsmelding = Oppdragsmelding(
            status = Oppdragsmelding.Oppdragsmeldingstatus.SENDT,
            originalMelding = "",
            avstemmingsnøkkel = Avstemmingsnøkkel(
                opprettet = Tidspunkt.now()
            )
        ),
        utbetalingslinjer = utbetalingslinjer,
        fnr = fnr,
        type = type,
        simulering = Simulering(
            gjelderId = Fnr(fnr = fnr.toString()),
            gjelderNavn = "navn",
            datoBeregnet = idag(),
            nettoBeløp = 0,
            periodeList = listOf()
        )
    )
}
