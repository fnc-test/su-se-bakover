package no.nav.su.se.bakover.service.utbetaling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.toKvittertUtbetaling
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import java.util.UUID

internal class UtbetalingServiceImplTest {

    @Test
    fun `hent utbetaling - ikke funnet`() {
        val utbetalingRepoMock = mock<UtbetalingRepo> { on { hentUtbetaling(any<UUID30>()) } doReturn null }

        UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakRepo = mock(),
            simuleringClient = mock()
        ).hentUtbetaling(UUID30.randomUUID()) shouldBe FantIkkeUtbetaling.left()

        verify(utbetalingRepoMock, Times(1)).hentUtbetaling(any<UUID30>())
    }

    @Test
    fun `hent utbetaling - funnet`() {
        val utbetaling = Utbetaling.UtbetalingForSimulering(
            utbetalingslinjer = listOf(),
            fnr = Fnr("12345678910"),
            type = Utbetaling.UtbetalingType.NY
        )
        val utbetalingRepoMock = mock<UtbetalingRepo> { on { hentUtbetaling(any<UUID30>()) } doReturn utbetaling }

        UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakRepo = mock(),
            simuleringClient = mock()
        ).hentUtbetaling(utbetaling.id) shouldBe utbetaling.right()

        verify(utbetalingRepoMock).hentUtbetaling(utbetaling.id)
    }

    @Test
    fun `oppdater med kvittering - ikke funnet`() {
        val kvittering = Kvittering(
            Kvittering.Utbetalingsstatus.OK,
            ""
        )

        val avstemmingsnøkkel = Avstemmingsnøkkel()

        val utbetalingRepoMock = mock<UtbetalingRepo> { on { hentUtbetaling(avstemmingsnøkkel) } doReturn null }

        UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakRepo = mock(),
            simuleringClient = mock()
        ).oppdaterMedKvittering(
            avstemmingsnøkkel = avstemmingsnøkkel,
            kvittering = kvittering
        ) shouldBe FantIkkeUtbetaling.left()

        verify(utbetalingRepoMock, Times(1)).hentUtbetaling(avstemmingsnøkkel)
        verify(utbetalingRepoMock, Times(0)).oppdaterMedKvittering(any(), any())
    }

    @Test
    fun `oppdater med kvittering - kvittering eksisterer ikke fra før`() {
        val avstemmingsnøkkel = Avstemmingsnøkkel()
        val utbetaling = Utbetaling.OversendtUtbetaling(
            utbetalingslinjer = listOf(),
            fnr = Fnr("12345678910"),
            oppdragsmelding = Oppdragsmelding(Oppdragsmelding.Oppdragsmeldingstatus.SENDT, "", avstemmingsnøkkel),
            simulering = Simulering(
                gjelderId = Fnr("12345678910"),
                gjelderNavn = "navn",
                datoBeregnet = idag(),
                nettoBeløp = 0,
                periodeList = listOf()
            ),
            type = Utbetaling.UtbetalingType.NY
        )
        val kvittering = Kvittering(
            Kvittering.Utbetalingsstatus.OK,
            ""
        )

        val postUpdate = utbetaling.toKvittertUtbetaling(kvittering)

        val utbetalingRepoMock = mock<UtbetalingRepo> {
            on { hentUtbetaling(avstemmingsnøkkel) } doReturn utbetaling
            on { oppdaterMedKvittering(utbetaling.id, kvittering) } doReturn postUpdate
        }

        UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakRepo = mock(),
            simuleringClient = mock()
        ).oppdaterMedKvittering(
            utbetaling.oppdragsmelding.avstemmingsnøkkel,
            kvittering
        ) shouldBe postUpdate.right()

        verify(utbetalingRepoMock, Times(1)).hentUtbetaling(avstemmingsnøkkel)
        verify(utbetalingRepoMock, Times(1)).oppdaterMedKvittering(utbetaling.id, kvittering)
    }

    @Test
    fun `oppdater med kvittering - kvittering eksisterer fra før`() {
        val avstemmingsnøkkel = Avstemmingsnøkkel()
        val utbetaling = Utbetaling.KvittertUtbetaling(
            utbetalingslinjer = listOf(),
            fnr = Fnr("12345678910"),
            oppdragsmelding = Oppdragsmelding(Oppdragsmelding.Oppdragsmeldingstatus.SENDT, "", avstemmingsnøkkel),
            simulering = Simulering(
                gjelderId = Fnr("12345678910"),
                gjelderNavn = "navn",
                datoBeregnet = idag(),
                nettoBeløp = 0,
                periodeList = listOf()
            ),
            type = Utbetaling.UtbetalingType.NY,
            kvittering = Kvittering(
                utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
                originalKvittering = ""
            )
        )

        val utbetalingRepoMock = mock<UtbetalingRepo> {
            on { hentUtbetaling(avstemmingsnøkkel) } doReturn utbetaling
        }

        val nyKvittering = Kvittering(
            utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
            originalKvittering = ""
        )

        UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakRepo = mock(),
            simuleringClient = mock()
        ).oppdaterMedKvittering(
            avstemmingsnøkkel,
            nyKvittering
        ) shouldBe utbetaling.right()

        verify(utbetalingRepoMock, Times(1)).hentUtbetaling(avstemmingsnøkkel)
        verify(utbetalingRepoMock, Times(0)).oppdaterMedKvittering(utbetaling.id, nyKvittering)
    }

    @Test
    fun `lag utbetaling for simulering`() {
        val sakId = UUID.randomUUID()
        val fnr = Fnr("12345678910")
        val beregning = Beregning(
            fraOgMed = 1.januar(2020),
            tilOgMed = 31.januar(2020),
            sats = Sats.HØY,
            fradrag = emptyList()
        )
        val sak = Sak(
            id = sakId, fnr = fnr,
            oppdrag = Oppdrag(
                id = UUID30.randomUUID(), opprettet = Tidspunkt.now(), sakId = sakId, utbetalinger = mutableListOf()
            )
        )

        val sakRepoMock = mock<SakRepo> {
            on { hentSak(sakId) } doReturn sak
        }

        val response = UtbetalingServiceImpl(
            utbetalingRepo = mock(),
            sakRepo = sakRepoMock,
            simuleringClient = mock()
        ).lagUtbetaling(sak.id, Oppdrag.UtbetalingStrategy.Ny(beregning))

        verify(sakRepoMock).hentSak(sakId)
        response.oppdrag shouldBe sak.oppdrag
        response.utbetaling shouldNotBe null
        response.attestant shouldBe Attestant("SU")
    }
}
