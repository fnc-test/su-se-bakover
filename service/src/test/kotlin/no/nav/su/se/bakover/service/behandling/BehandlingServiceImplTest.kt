package no.nav.su.se.bakover.service.behandling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.NavIdentBruker.Attestant
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Person.Navn
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandling.BehandlingsStatus.BEREGNET_AVSLAG
import no.nav.su.se.bakover.domain.behandling.Behandling.BehandlingsStatus.IVERKSATT_INNVILGET
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics.InnvilgetHandlinger
import no.nav.su.se.bakover.domain.behandling.avslag.Avslag
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagBrevRequest
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.PersonOppslag
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.createService
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.tidspunkt
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeDistribuereBrev
import no.nav.su.se.bakover.service.brev.KunneIkkeJournalføreBrev
import no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeUtbetale
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import java.util.UUID

internal class BehandlingServiceImplTest {
    private val sakId = UUID.randomUUID()
    private val søknadId = UUID.randomUUID()
    private val behandlingId = UUID.randomUUID()
    private val fnr = Fnr("12345678910")
    private val saksbehandler = Saksbehandler("AB12345")
    private val oppgaveId = OppgaveId("o")
    private val journalpostId = JournalpostId("j")
    private val brevbestillingId = BrevbestillingId("2")
    private val person = Person(
        ident = Ident(
            fnr = Fnr(fnr = "12345678901"),
            aktørId = AktørId(aktørId = "123")
        ),
        navn = Navn(fornavn = "Tore", mellomnavn = "Johnas", etternavn = "Strømøy"),
        telefonnummer = null,
        adresse = null,
        statsborgerskap = null,
        kjønn = null,
        adressebeskyttelse = null,
        skjermet = null,
        kontaktinfo = null,
        vergemål = null,
        fullmakt = null,
    )

    @Test
    fun `simuler behandling`() {
        val behandling = beregnetBehandling()

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerUtbetaling(any(), any(), any()) } doReturn simulertUtbetaling.right()
        }
        behandling.simulering() shouldBe null
        behandling.status() shouldBe Behandling.BehandlingsStatus.BEREGNET_INNVILGET

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
        ).simuler(behandling.id, saksbehandler)

        response shouldBe behandling.right()
        verify(behandlingRepoMock, Times(2)).hentBehandling(behandling.id)
        verify(utbetalingServiceMock).simulerUtbetaling(
            sakId = argThat { it shouldBe behandling.sakId },
            saksbehandler = argThat { it shouldBe saksbehandler },
            beregning = argThat { it shouldBe beregning }
        )
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
            on { simulerUtbetaling(any(), any(), any()) } doReturn SimuleringFeilet.TEKNISK_FEIL.left()
        }

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
        ).simuler(behandling.id, saksbehandler)

        response shouldBe SimuleringFeilet.TEKNISK_FEIL.left()
        verify(behandlingRepoMock).hentBehandling(
            argThat { it shouldBe behandling.id }
        )
        verify(utbetalingServiceMock).simulerUtbetaling(
            sakId = argThat { it shouldBe behandling.sakId },
            saksbehandler = argThat { it shouldBe saksbehandler },
            beregning = argThat { it shouldBe beregning }
        )
        verifyNoMoreInteractions(behandlingRepoMock)
    }

    @Test
    fun `iverksett behandling betaler ikke ut penger ved avslag`() {
        val behandling = behandlingTilAttestering(Behandling.BehandlingsStatus.TIL_ATTESTERING_AVSLAG)

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val personOppslagMock: PersonOppslag = mock {
            on { person(any()) } doReturn person.right()
        }

        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn journalpostId.right()
            on { distribuerBrev(any()) } doReturn brevbestillingId.right()
        }

        val oppgaveServiceMock: OppgaveService = mock {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val utbetalingServiceMock = mock<UtbetalingService>()

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
            brevService = brevServiceMock,
            personOppslag = personOppslagMock,
            oppgaveService = oppgaveServiceMock,
        ).iverksett(behandling.id, attestant)

        response shouldBe IverksattBehandling.UtenMangler(behandling).right()
        inOrder(behandlingRepoMock, brevServiceMock, personOppslagMock, oppgaveServiceMock) {
            verify(behandlingRepoMock).hentBehandling(behandling.id)
            verify(personOppslagMock).person(argThat { it shouldBe fnr })
            verify(brevServiceMock).journalførBrev(
                argThat {
                    it shouldBe AvslagBrevRequest(
                        person,
                        Avslag(
                            opprettet = tidspunkt,
                            avslagsgrunner = listOf(),
                            harEktefelle = false,
                            beregning = beregning

                        )
                    )
                },
                argThat { it shouldBe behandling.sakId }
            )
            verify(behandlingRepoMock).oppdaterIverksattJournalpostId(
                behandlingId = argThat { it shouldBe behandling.id },
                journalpostId = argThat { it shouldBe journalpostId }
            )
            verify(behandlingRepoMock).oppdaterAttestant(behandling.id, attestant)
            verify(behandlingRepoMock).oppdaterBehandlingStatus(behandling.id, Behandling.BehandlingsStatus.IVERKSATT_AVSLAG)

            verify(brevServiceMock).distribuerBrev(journalpostId)
            verify(behandlingRepoMock).oppdaterIverksattBrevbestillingId(
                behandlingId = argThat { it shouldBe behandling.id },
                bestillingId = argThat { it shouldBe brevbestillingId }
            )
            verify(oppgaveServiceMock).lukkOppgave(oppgaveId)
        }
        verifyNoMoreInteractions(behandlingRepoMock, brevServiceMock, personOppslagMock, oppgaveServiceMock, utbetalingServiceMock)
    }

    @Test
    fun `avslag blir ikke iverksatt dersom journalføring av brev feiler`() {
        val behandling = behandlingTilAttestering(Behandling.BehandlingsStatus.TIL_ATTESTERING_AVSLAG)

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val personOppslagMock: PersonOppslag = mock {
            on { person(any()) } doReturn person.right()
        }

        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost.left()
        }

        val oppgaveServiceMock: OppgaveService = mock {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val utbetalingServiceMock: UtbetalingService = mock()

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            brevService = brevServiceMock,
            personOppslag = personOppslagMock,
            oppgaveService = oppgaveServiceMock,
            utbetalingService = utbetalingServiceMock
        ).iverksett(behandling.id, attestant)

        response shouldBe KunneIkkeIverksetteBehandling.KunneIkkeJournalføreBrev.left()
        inOrder(behandlingRepoMock, brevServiceMock, personOppslagMock, oppgaveServiceMock) {
            verify(behandlingRepoMock).hentBehandling(behandling.id)
            verify(personOppslagMock).person(argThat { it shouldBe fnr })
            verify(brevServiceMock).journalførBrev(
                request = argThat {
                    it shouldBe AvslagBrevRequest(
                        person,
                        Avslag(
                            opprettet = tidspunkt,
                            avslagsgrunner = listOf(),
                            harEktefelle = false,
                            beregning = beregning

                        )
                    )
                },
                sakId = argThat { it shouldBe behandling.sakId }
            )
        }
        verifyNoMoreInteractions(behandlingRepoMock, brevServiceMock, personOppslagMock, oppgaveServiceMock, utbetalingServiceMock)
    }

    @Test
    fun `Innvilgelse blir iverksatt med mangler dersom brev feiler`() {
        val behandling = behandlingTilAttestering(Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET)

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { utbetal(any(), any(), any(), any()) } doReturn oversendtUtbetaling.right()
        }

        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn journalpostId.right()
            on { distribuerBrev(any()) } doReturn KunneIkkeDistribuereBrev.left()
        }

        val personOppslagMock = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }

        val oppgaveServiceMock: OppgaveService = mock {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
            oppgaveService = oppgaveServiceMock,
            personOppslag = personOppslagMock,
            brevService = brevServiceMock,
        ).iverksett(behandling.id, attestant)

        response shouldBe IverksattBehandling.MedMangler.KunneIkkeDistribuereBrev(behandling).right()

        inOrder(behandlingRepoMock, brevServiceMock, utbetalingServiceMock, personOppslagMock, oppgaveServiceMock) {
            verify(behandlingRepoMock).hentBehandling(behandling.id)
            verify(personOppslagMock).person(argThat { it shouldBe fnr })
            verify(utbetalingServiceMock).utbetal(
                sakId = argThat { it shouldBe sakId },
                attestant = argThat { it shouldBe attestant },
                beregning = argThat { it shouldBe beregning },
                simulering = argThat { it shouldBe simulering },
            )
            verify(behandlingRepoMock).leggTilUtbetaling(behandling.id, utbetalingForSimulering.id)
            verify(behandlingRepoMock).oppdaterAttestant(behandling.id, attestant)
            verify(behandlingRepoMock).oppdaterBehandlingStatus(
                behandling.id,
                Behandling.BehandlingsStatus.IVERKSATT_INNVILGET
            )
            verify(brevServiceMock).journalførBrev(
                request = argThat { it shouldBe LagBrevRequest.InnvilgetVedtak(person, behandling) },
                sakId = argThat { it shouldBe behandling.sakId }
            )
            verify(behandlingRepoMock).oppdaterIverksattJournalpostId(
                behandlingId = argThat { it shouldBe behandling.id },
                journalpostId = argThat { it shouldBe journalpostId }
            )
            verify(brevServiceMock).distribuerBrev(argThat { it shouldBe journalpostId })
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe oppgaveId })
        }
        verifyNoMoreInteractions(behandlingRepoMock, brevServiceMock, personOppslagMock, oppgaveServiceMock, utbetalingServiceMock)
    }

    @Test
    fun `iverksett behandling betaler ut penger ved innvilgelse`() {
        val behandling = behandlingTilAttestering(Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET)

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn journalpostId.right()
            on { distribuerBrev(any()) } doReturn brevbestillingId.right()
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                utbetal(
                    any(), any(), any(), any()
                )
            } doReturn oversendtUtbetaling.right()
        }

        val personOppslagMock: PersonOppslag = mock {
            on { person(any()) } doReturn person.right()
        }
        val oppgaveServiceMock: OppgaveService = mock {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val behandlingMetricsMock: BehandlingMetrics = mock()

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
            oppgaveService = oppgaveServiceMock,
            personOppslag = personOppslagMock,
            brevService = brevServiceMock,
            behandlingMetrics = behandlingMetricsMock
        ).iverksett(behandling.id, attestant)

        response shouldBe IverksattBehandling.UtenMangler(behandling).right()

        inOrder(
            behandlingRepoMock, utbetalingServiceMock, personOppslagMock, brevServiceMock, behandlingMetricsMock, oppgaveServiceMock
        ) {
            verify(behandlingRepoMock).hentBehandling(argThat { it shouldBe behandling.id })
            verify(personOppslagMock).person(argThat { it shouldBe fnr })
            verify(utbetalingServiceMock).utbetal(
                sakId = argThat { it shouldBe behandling.sakId },
                attestant = argThat { it shouldBe attestant },
                beregning = argThat { it shouldBe beregning },
                simulering = argThat { it shouldBe simulering }
            )
            verify(behandlingRepoMock).leggTilUtbetaling(behandling.id, utbetalingForSimulering.id)
            verify(behandlingRepoMock).oppdaterAttestant(behandling.id, attestant)
            verify(behandlingRepoMock).oppdaterBehandlingStatus(
                behandling.id,
                Behandling.BehandlingsStatus.IVERKSATT_INNVILGET
            )
            verify(behandlingMetricsMock).incrementInnvilgetCounter(InnvilgetHandlinger.PERSISTERT)
            verify(brevServiceMock).journalførBrev(LagBrevRequest.InnvilgetVedtak(person, behandling), behandling.sakId)

            verify(behandlingRepoMock).oppdaterIverksattJournalpostId(
                behandlingId = argThat { it shouldBe behandling.id },
                journalpostId = argThat { it shouldBe journalpostId }
            )
            verify(behandlingMetricsMock).incrementInnvilgetCounter(InnvilgetHandlinger.JOURNALFØRT)
            verify(brevServiceMock).distribuerBrev(journalpostId)

            verify(behandlingRepoMock).oppdaterIverksattBrevbestillingId(
                behandlingId = argThat { it shouldBe behandling.id },
                bestillingId = argThat { it shouldBe brevbestillingId }
            )
            verify(behandlingMetricsMock).incrementInnvilgetCounter(InnvilgetHandlinger.DISTRIBUERT_BREV)
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe oppgaveId })
            verify(behandlingMetricsMock).incrementInnvilgetCounter(InnvilgetHandlinger.LUKKET_OPPGAVE)
        }
        verifyNoMoreInteractions(behandlingRepoMock, utbetalingServiceMock, personOppslagMock, behandlingMetricsMock, oppgaveServiceMock)
    }

    @Test
    fun `iverksett behandling gir feilmelding ved inkonsistens i simuleringsresultat`() {
        val behandling = behandlingTilAttestering(Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET)

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val personOppslagMock: PersonOppslag = mock {
            on { person(any()) } doReturn person.right()
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                utbetal(
                    behandling.sakId,
                    attestant,
                    beregning,
                    simulering
                )
            } doReturn KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte.left()
        }

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
            personOppslag = personOppslagMock
        ).iverksett(behandling.id, attestant)

        response shouldBe KunneIkkeIverksetteBehandling.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte.left()

        inOrder(behandlingRepoMock, personOppslagMock, utbetalingServiceMock) {
            verify(behandlingRepoMock).hentBehandling(behandling.id)
            verify(personOppslagMock).person(argThat { it shouldBe fnr })
            verify(utbetalingServiceMock).utbetal(behandling.sakId, attestant, beregning, simulering)
            verify(behandlingRepoMock, Times(0)).oppdaterBehandlingStatus(any(), any())
        }
        verifyNoMoreInteractions(behandlingRepoMock, personOppslagMock, utbetalingServiceMock)
    }

    @Test
    fun `iverksett behandling gir feilmelding dersom vi ikke får sendt utbetaling til oppdrag`() {
        val behandling = behandlingTilAttestering(Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET)

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val personOppslagMock: PersonOppslag = mock {
            on { person(any()) } doReturn person.right()
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                utbetal(
                    sakId = argThat { it shouldBe behandling.sakId },
                    attestant = argThat { it shouldBe attestant },
                    beregning = argThat { it shouldBe beregning },
                    simulering = argThat { it shouldBe simulering }
                )
            } doReturn KunneIkkeUtbetale.Protokollfeil.left()
        }

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
            personOppslag = personOppslagMock
        ).iverksett(behandling.id, attestant)

        response shouldBe KunneIkkeIverksetteBehandling.KunneIkkeUtbetale.left()

        inOrder(behandlingRepoMock, personOppslagMock, utbetalingServiceMock) {
            verify(behandlingRepoMock).hentBehandling(
                argThat {
                    it shouldBe behandling.id
                }
            )
            verify(personOppslagMock).person(argThat { it shouldBe fnr })
            verify(utbetalingServiceMock).utbetal(
                sakId = argThat { it shouldBe behandling.sakId },
                attestant = argThat { it shouldBe attestant },
                beregning = argThat { it shouldBe beregning },
                simulering = argThat { it shouldBe simulering }
            )
        }
        verifyNoMoreInteractions(behandlingRepoMock, personOppslagMock, utbetalingServiceMock)
    }

    @Test
    fun `lager brevutkast for avslag`() {
        val pdf = "pdf-doc".toByteArray()
        val behandling = beregnetBehandling().copy(status = BEREGNET_AVSLAG)
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }
        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn pdf.right()
        }
        val personOppslagMock = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }
        val response = createService(
            behandlingRepo = behandlingRepoMock,
            brevService = brevServiceMock,
            personOppslag = personOppslagMock,
        ).lagBrevutkast(behandlingId)

        response shouldBe pdf.right()
        verify(brevServiceMock).lagBrev(argThat { it.shouldBeTypeOf<AvslagBrevRequest>() })
    }

    @Test
    fun `lager brevutkast for innvilgelse`() {
        val pdf = "pdf-doc".toByteArray()
        val behandling = behandlingTilAttestering(IVERKSATT_INNVILGET)
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }
        val brevServiceMock = mock<BrevService>() {
            on { lagBrev(any()) } doReturn pdf.right()
        }
        val personOppslagMock = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }
        val response = createService(
            behandlingRepo = behandlingRepoMock,
            brevService = brevServiceMock,
            personOppslag = personOppslagMock,
        ).lagBrevutkast(behandlingId)

        response shouldBe pdf.right()
        verify(personOppslagMock).person(argThat { it shouldBe fnr })
        verify(brevServiceMock).lagBrev(argThat { it.shouldBeTypeOf<LagBrevRequest.InnvilgetVedtak>() })
    }

    @Test
    fun `svarer med feil dersom behandling ikke finnes`() {
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn null
        }
        val response = createService(
            behandlingRepo = behandlingRepoMock,
        ).lagBrevutkast(behandlingId)

        response shouldBe KunneIkkeLageBrevutkast.FantIkkeBehandling.left()
    }

    @Test
    fun `svarer med feil dersom laging av brev feiler`() {
        val behandling = behandlingTilAttestering(IVERKSATT_INNVILGET)
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }
        val brevServiceMock = mock<BrevService>() {
            on { lagBrev(any()) } doReturn KunneIkkeLageBrev.KunneIkkeGenererePDF.left()
        }
        val personOppslagMock = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }
        val response = createService(
            behandlingRepo = behandlingRepoMock,
            brevService = brevServiceMock,
            personOppslag = personOppslagMock
        ).lagBrevutkast(behandlingId)

        response shouldBe KunneIkkeLageBrevutkast.KunneIkkeLageBrev.left()
    }

    private fun beregnetBehandling() = BehandlingFactory(mock()).createBehandling(
        sakId = sakId,
        søknad = Søknad.Journalført.MedOppgave(
            id = søknadId,
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId,
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            oppgaveId = oppgaveId,
            journalpostId = journalpostId,
        ),
        status = Behandling.BehandlingsStatus.BEREGNET_INNVILGET,
        beregning = beregning,
        fnr = fnr,
        oppgaveId = oppgaveId
    )

    private fun behandlingTilAttestering(status: Behandling.BehandlingsStatus) = beregnetBehandling().copy(
        simulering = simulering,
        status = status
    )

    private val attestant = Attestant("SU")

    private val oppdragsmelding = Utbetalingsrequest(
        value = ""
    )

    private val beregning = TestBeregning

    private val oppdrag = Oppdrag(
        id = UUID30.randomUUID(),
        opprettet = Tidspunkt.now(),
        sakId = sakId,
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
        type = Utbetaling.UtbetalingsType.NY,
        oppdragId = oppdrag.id,
        behandler = attestant,
        avstemmingsnøkkel = Avstemmingsnøkkel()
    )

    private val simulertUtbetaling = utbetalingForSimulering.toSimulertUtbetaling(simulering)
    private val oversendtUtbetaling = simulertUtbetaling.toOversendtUtbetaling(oppdragsmelding)
}
