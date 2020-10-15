package no.nav.su.se.bakover.service.behandling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.database.beregning.BeregningRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepo
import no.nav.su.se.bakover.database.oppdrag.OppdragRepo
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.toOversendtUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.toSimulertUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import java.util.UUID

internal class BehandlingServiceImplTest {

    private val sakId = UUID.randomUUID()
    private val fnr = Fnr("12345678910")

    @Test
    fun `simuler behandling`() {
        val behandling = beregnetBehandling()

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { lagUtbetaling(behandling.sakId, strategy) } doReturn nyUtbetaling
            on { simulerUtbetaling(nyUtbetaling) } doReturn simulertUtbetaling.right()
        }
        behandling.simulering() shouldBe null
        behandling.status() shouldBe Behandling.BehandlingsStatus.BEREGNET_INNVILGET

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
        ).simuler(behandling.id)

        response shouldBe behandling.right()
        verify(behandlingRepoMock, Times(2)).hentBehandling(behandling.id)
        verify(utbetalingServiceMock).lagUtbetaling(behandling.sakId, strategy)
        verify(utbetalingServiceMock).simulerUtbetaling(nyUtbetaling)
        verify(behandlingRepoMock).leggTilSimulering(behandling.id, simulering)
        verify(behandlingRepoMock).oppdaterBehandlingStatus(behandling.id, Behandling.BehandlingsStatus.SIMULERT)
    }

    @Test
    fun `simuler behandling gir feilmelding hvis simulering ikke går bra`() {
        val behandling = beregnetBehandling()

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { lagUtbetaling(behandling.sakId, strategy) } doReturn nyUtbetaling
            on { simulerUtbetaling(nyUtbetaling) } doReturn SimuleringFeilet.TEKNISK_FEIL.left()
        }

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
        ).simuler(behandling.id)

        response shouldBe SimuleringFeilet.TEKNISK_FEIL.left()
        verify(behandlingRepoMock, Times(1)).hentBehandling(behandling.id)
        verify(utbetalingServiceMock).lagUtbetaling(behandling.sakId, strategy)
        verify(utbetalingServiceMock).simulerUtbetaling(nyUtbetaling)
        verifyNoMoreInteractions(behandlingRepoMock)
    }

    @Test
    fun `iverksett behandling betaler ikke ut penger ved avslag`() {
        val behandling = behandlingTilAttestering(Behandling.BehandlingsStatus.TIL_ATTESTERING_AVSLAG)

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val utbetalingSericeMock = mock<UtbetalingService>()
        val utbetalingPublisherMock = mock<UtbetalingPublisher>()

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            utbetalingService = utbetalingSericeMock,
            utbetalingPublisher = utbetalingPublisherMock
        ).iverksett(behandling.id, Attestant("Z123456"))

        response shouldBe behandling.right()
        verify(behandlingRepoMock).hentBehandling(behandling.id)
        verify(behandlingRepoMock).attester(behandling.id, Attestant("Z123456"))
        verify(behandlingRepoMock).oppdaterBehandlingStatus(
            behandling.id,
            Behandling.BehandlingsStatus.IVERKSATT_AVSLAG
        )
        verifyZeroInteractions(utbetalingSericeMock, utbetalingPublisherMock)
    }

    @Test
    fun `iverksett behandling betaler ut penger ved innvilgelse`() {
        val behandling = behandlingTilAttestering(Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET)

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { lagUtbetaling(behandling.sakId, strategy) } doReturn nyUtbetaling
            on { simulerUtbetaling(nyUtbetaling) } doReturn simulertUtbetaling.right()
        }

        val utbetalingPublisherMock = mock<UtbetalingPublisher>() {
            on {
                publish(
                    argThat {
                        it.oppdrag shouldBe oppdrag
                        it.utbetaling shouldBe utbetalingForSimulering
                        it.attestant shouldBe Attestant("Z123456")
                        it.avstemmingsnøkkel shouldNotBe null
                    }
                )
            } doReturn oppdragsmelding.right()
        }

        val oppdragRepoMock = mock<OppdragRepo>() {
            on { hentOppdrag(sak.id) } doReturn oppdrag
        }

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
            utbetalingPublisher = utbetalingPublisherMock,
            oppdragRepo = oppdragRepoMock
        ).iverksett(behandling.id, Attestant("Z123456"))

        response shouldBe behandling.right()

        inOrder(
            behandlingRepoMock, utbetalingServiceMock, utbetalingPublisherMock, behandlingRepoMock
        ) {
            verify(behandlingRepoMock).hentBehandling(behandling.id)
            verify(utbetalingServiceMock).lagUtbetaling(behandling.sakId, strategy)
            verify(utbetalingServiceMock).simulerUtbetaling(nyUtbetaling)
            verify(utbetalingPublisherMock).publish(any())
            verify(utbetalingServiceMock).opprettUtbetaling(sak.oppdrag.id, oversendtUtbetaling)
            verify(behandlingRepoMock).leggTilUtbetaling(behandling.id, utbetalingForSimulering.id)
            verify(behandlingRepoMock).attester(behandling.id, Attestant("Z123456"))
            verify(behandlingRepoMock).oppdaterBehandlingStatus(
                behandling.id,
                Behandling.BehandlingsStatus.IVERKSATT_INNVILGET
            )
        }
    }

    @Test
    fun `iverksett behandling gir feilmelding ved inkonsistens i simuleringsresultat`() {
        val behandling = behandlingTilAttestering(Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET)

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { lagUtbetaling(behandling.sakId, strategy) } doReturn nyUtbetaling
            on { simulerUtbetaling(nyUtbetaling) } doReturn simulertUtbetaling.copy(simulering = simulering.copy(nettoBeløp = 4)).right()
        }

        val utbetalingPublisherMock = mock<UtbetalingPublisher>()

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
            utbetalingPublisher = utbetalingPublisherMock
        ).iverksett(behandling.id, Attestant("Z123456"))

        response shouldBe Behandling.IverksettFeil.InkonsistentSimuleringsResultat().left()

        verify(behandlingRepoMock).hentBehandling(behandling.id)
        verify(utbetalingServiceMock).lagUtbetaling(behandling.sakId, strategy)
        verify(utbetalingServiceMock).simulerUtbetaling(nyUtbetaling)
        verify(behandlingRepoMock, Times(0)).oppdaterBehandlingStatus(any(), any())
        verifyNoMoreInteractions(utbetalingServiceMock)
        verifyZeroInteractions(utbetalingPublisherMock)
    }

    @Test
    fun `iverksett behandling gir feilmelding dersom vi ikke får sendt utbetaling til oppdrag`() {
        val behandling = behandlingTilAttestering(Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET)

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { lagUtbetaling(behandling.sakId, strategy) } doReturn nyUtbetaling
            on { simulerUtbetaling(nyUtbetaling) } doReturn simulertUtbetaling.right()
        }

        val oppdragRepoMock = mock<OppdragRepo>() {
            on { hentOppdrag(behandling.sakId) } doReturn oppdrag
        }

        val utbetalingPublisherMock = mock<UtbetalingPublisher>() {
            on { publish(any()) } doReturn UtbetalingPublisher.KunneIkkeSendeUtbetaling(
                oppdragsmelding.copy(Oppdragsmelding.Oppdragsmeldingstatus.FEIL)
            ).left()
        }

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
            utbetalingPublisher = utbetalingPublisherMock,
            oppdragRepo = oppdragRepoMock
        ).iverksett(behandling.id, Attestant("Z123456"))

        response shouldBe Behandling.IverksettFeil.Utbetaling().left()
    }

    private fun beregnetBehandling() = Behandling(
        sakId = sakId,
        søknad = Søknad(søknadInnhold = SøknadInnholdTestdataBuilder.build()),
        status = Behandling.BehandlingsStatus.BEREGNET_INNVILGET,
        beregning = beregning
    )

    private fun behandlingTilAttestering(status: Behandling.BehandlingsStatus) = beregnetBehandling().copy(
        simulering = simulering,
        status = status
    )

    private val avstemmingsnøkkel = Avstemmingsnøkkel()

    private val oppdragsmelding = Oppdragsmelding(
        status = Oppdragsmelding.Oppdragsmeldingstatus.SENDT,
        originalMelding = "",
        avstemmingsnøkkel = avstemmingsnøkkel
    )

    private val beregning = Beregning(
        fraOgMed = 1.januar(2020),
        tilOgMed = 31.januar(2020),
        sats = Sats.HØY,
        fradrag = listOf()
    )

    private val strategy = Oppdrag.UtbetalingStrategy.Ny(beregning)

    private val oppdrag = Oppdrag(
        id = UUID30.randomUUID(),
        opprettet = Tidspunkt.now(),
        sakId = sakId,
    )

    private val sak = Sak(
        id = sakId,
        fnr = fnr,
        oppdrag = oppdrag
    )

    private val simulering = Simulering(
        gjelderId = fnr,
        gjelderNavn = "NAVN",
        datoBeregnet = idag(),
        nettoBeløp = 191500,
        periodeList = listOf()
    )

    private val utbetalingForSimulering = Utbetaling.UtbetalingForSimulering(
        utbetalingslinjer = listOf(),
        fnr = fnr,
        type = Utbetaling.UtbetalingType.NY
    )

    private val simulertUtbetaling = utbetalingForSimulering.toSimulertUtbetaling(simulering)
    private val oversendtUtbetaling = simulertUtbetaling.toOversendtUtbetaling(oppdragsmelding)

    private val nyUtbetaling = NyUtbetaling(
        oppdrag = oppdrag,
        utbetaling = utbetalingForSimulering,
        attestant = Attestant("SU")
    )

    private fun createService(
        behandlingRepo: BehandlingRepo = mock(),
        hendelsesloggRepo: HendelsesloggRepo = mock(),
        beregningRepo: BeregningRepo = mock(),
        oppdragRepo: OppdragRepo = mock(),
        utbetalingService: UtbetalingService = mock(),
        oppgaveClient: OppgaveClient = mock(),
        utbetalingPublisher: UtbetalingPublisher = mock(),
        søknadService: SøknadService = mock(),
        sakService: SakService = mock(),
        personOppslag: PersonOppslag = mock()
    ) = BehandlingServiceImpl(
        behandlingRepo = behandlingRepo,
        hendelsesloggRepo = hendelsesloggRepo,
        beregningRepo = beregningRepo,
        oppdragRepo = oppdragRepo,
        utbetalingService = utbetalingService,
        oppgaveClient = oppgaveClient,
        utbetalingPublisher = utbetalingPublisher,
        søknadService = søknadService,
        sakService = sakService,
        personOppslag = personOppslag
    )
}
