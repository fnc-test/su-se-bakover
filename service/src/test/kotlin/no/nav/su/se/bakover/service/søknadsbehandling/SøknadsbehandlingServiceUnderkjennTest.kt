package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.attestering.UnderkjennAttesteringsgrunnBehandling
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.UnderkjentSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.underkjenn.KunneIkkeUnderkjenneSøknadsbehandling
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.tilAttesteringSøknadsbehandlingUføre
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import person.domain.KunneIkkeHentePerson
import person.domain.PersonService
import java.util.UUID

class SøknadsbehandlingServiceUnderkjennTest {
    private val fnr = Fnr.generer()
    private val nyOppgaveId = OppgaveId("999")
    private val aktørId = AktørId("12345")

    private val underkjentAttestering = Attestering.Underkjent(
        attestant = NavIdentBruker.Attestant("a"),
        grunn = UnderkjennAttesteringsgrunnBehandling.ANDRE_FORHOLD,
        kommentar = "begrunnelse",
        opprettet = fixedTidspunkt,
    )

    private val innvilgetBehandlingTilAttestering = tilAttesteringSøknadsbehandlingUføre(
        sakInfo = SakInfo(
            sakId = UUID.randomUUID(),
            saksnummer = Saksnummer(2021),
            fnr = fnr,
            type = Sakstype.UFØRE,
        ),
        stønadsperiode = Stønadsperiode.create(år(2021)),
    ).second as SøknadsbehandlingTilAttestering.Innvilget

    private val oppgaveConfig = OppgaveConfig.Søknad(
        sakstype = innvilgetBehandlingTilAttestering.sakstype,
        journalpostId = innvilgetBehandlingTilAttestering.søknad.journalpostId,
        søknadId = innvilgetBehandlingTilAttestering.søknad.id,
        aktørId = aktørId,
        tilordnetRessurs = innvilgetBehandlingTilAttestering.saksbehandler,
        clock = fixedClock,
    )

    @Test
    fun `Fant ikke behandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn null
        }

        val personServiceMock = mock<PersonService>()
        val oppgaveServiceMock = mock<OppgaveService>()
        val behandlingMetricsMock = mock<BehandlingMetrics>()

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            behandlingMetrics = behandlingMetricsMock,
        ).underkjenn(
            SøknadsbehandlingService.UnderkjennRequest(
                behandlingId = innvilgetBehandlingTilAttestering.id,
                attestering = underkjentAttestering,
            ),
        )

        actual shouldBe KunneIkkeUnderkjenneSøknadsbehandling.FantIkkeBehandling.left()

        inOrder(søknadsbehandlingRepoMock) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
        }

        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
        )
    }

    @Test
    fun `Feil behandlingsstatus`() {
        val behandling: IverksattSøknadsbehandling.Innvilget = iverksattSøknadsbehandlingUføre().second as IverksattSøknadsbehandling.Innvilget

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val personServiceMock = mock<PersonService>()
        val oppgaveServiceMock = mock<OppgaveService>()
        val behandlingMetricsMock = mock<BehandlingMetrics>()

        createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            behandlingMetrics = behandlingMetricsMock,
        ).underkjenn(
            SøknadsbehandlingService.UnderkjennRequest(
                behandlingId = behandling.id,
                attestering = underkjentAttestering,
            ),
        ) shouldBe KunneIkkeUnderkjenneSøknadsbehandling.UgyldigTilstand(
            fra = behandling::class,
        ).left()

        inOrder(søknadsbehandlingRepoMock) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandling.id })
        }

        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
        )
    }

    @Test
    fun `attestant kan ikke være den samme som saksbehandler`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn innvilgetBehandlingTilAttestering
        }

        val attestantSomErLikSaksbehandler =
            NavIdentBruker.Attestant(innvilgetBehandlingTilAttestering.saksbehandler.navIdent)

        val personServiceMock = mock<PersonService>()
        val oppgaveServiceMock = mock<OppgaveService>()
        val behandlingMetricsMock = mock<BehandlingMetrics>()
        val observerMock: StatistikkEventObserver = mock()

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            behandlingMetrics = behandlingMetricsMock,
            observer = observerMock,
        ).underkjenn(
            SøknadsbehandlingService.UnderkjennRequest(
                behandlingId = innvilgetBehandlingTilAttestering.id,
                attestering = Attestering.Underkjent(
                    attestant = attestantSomErLikSaksbehandler,
                    grunn = underkjentAttestering.grunn,
                    kommentar = underkjentAttestering.kommentar,
                    opprettet = fixedTidspunkt,
                ),
            ),
        )

        actual shouldBe KunneIkkeUnderkjenneSøknadsbehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()

        inOrder(søknadsbehandlingRepoMock) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
        }

        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
        )
        verifyNoInteractions(observerMock)
    }

    @Test
    fun `Feiler å underkjenne dersom vi ikke fikk aktør id`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn innvilgetBehandlingTilAttestering
        }

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }
        val oppgaveServiceMock = mock<OppgaveService>()
        val behandlingMetricsMock = mock<BehandlingMetrics>()

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            behandlingMetrics = behandlingMetricsMock,
        ).underkjenn(
            SøknadsbehandlingService.UnderkjennRequest(
                behandlingId = innvilgetBehandlingTilAttestering.id,
                attestering = underkjentAttestering,
            ),
        )

        actual shouldBe KunneIkkeUnderkjenneSøknadsbehandling.FantIkkeAktørId.left()

        inOrder(søknadsbehandlingRepoMock, personServiceMock) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
        }

        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
        )
    }

    @Test
    fun `Klarer ikke opprette oppgave`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn innvilgetBehandlingTilAttestering
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
            on { opprettOppgave(any()) } doReturn KunneIkkeOppretteOppgave.left()
        }
        val behandlingMetricsMock = mock<BehandlingMetrics>()

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            behandlingMetrics = behandlingMetricsMock,
        ).underkjenn(
            SøknadsbehandlingService.UnderkjennRequest(
                behandlingId = innvilgetBehandlingTilAttestering.id,
                attestering = underkjentAttestering,
            ),
        )

        actual shouldBe KunneIkkeUnderkjenneSøknadsbehandling.KunneIkkeOppretteOppgave.left()

        inOrder(søknadsbehandlingRepoMock, personServiceMock, oppgaveServiceMock) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
            verify(oppgaveServiceMock).opprettOppgave(argThat { it shouldBe oppgaveConfig })
        }

        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
        )
    }

    @Test
    fun `Underkjenner selvom vi ikke klarer lukke oppgave`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn innvilgetBehandlingTilAttestering
            on { hentForSak(any(), anyOrNull()) } doReturn listOf(innvilgetBehandlingTilAttestering)
        }

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn nyOppgaveId.right()
            on { lukkOppgave(any()) } doAnswer { KunneIkkeLukkeOppgave(it.getArgument(0)).left() }
        }
        val behandlingMetricsMock = mock<BehandlingMetrics>()
        val observerMock: StatistikkEventObserver = mock()

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            behandlingMetrics = behandlingMetricsMock,
            observer = observerMock,
        ).underkjenn(
            SøknadsbehandlingService.UnderkjennRequest(
                behandlingId = innvilgetBehandlingTilAttestering.id,
                attestering = underkjentAttestering,
            ),
        )

        val underkjentMedNyOppgaveIdOgAttestering = UnderkjentSøknadsbehandling.Innvilget(
            id = innvilgetBehandlingTilAttestering.id,
            opprettet = innvilgetBehandlingTilAttestering.opprettet,
            sakId = innvilgetBehandlingTilAttestering.sakId,
            saksnummer = innvilgetBehandlingTilAttestering.saksnummer,
            søknad = innvilgetBehandlingTilAttestering.søknad,
            oppgaveId = nyOppgaveId,
            fnr = innvilgetBehandlingTilAttestering.fnr,
            beregning = innvilgetBehandlingTilAttestering.beregning,
            simulering = innvilgetBehandlingTilAttestering.simulering,
            saksbehandler = innvilgetBehandlingTilAttestering.saksbehandler,
            attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(underkjentAttestering),
            fritekstTilBrev = "",
            aldersvurdering = innvilgetBehandlingTilAttestering.aldersvurdering,
            grunnlagsdataOgVilkårsvurderinger = innvilgetBehandlingTilAttestering.grunnlagsdataOgVilkårsvurderinger,
            avkorting = AvkortingVedSøknadsbehandling.IngenAvkorting,
            sakstype = innvilgetBehandlingTilAttestering.sakstype,
            søknadsbehandlingsHistorikk = innvilgetBehandlingTilAttestering.søknadsbehandlingsHistorikk,
        )

        actual shouldBe underkjentMedNyOppgaveIdOgAttestering.right()

        inOrder(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
            observerMock,
        ) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
            verify(oppgaveServiceMock).opprettOppgave(
                argThat {
                    it shouldBe oppgaveConfig
                },
            )
            verify(behandlingMetricsMock).incrementUnderkjentCounter(BehandlingMetrics.UnderkjentHandlinger.OPPRETTET_OPPGAVE)
            verify(søknadsbehandlingRepoMock).defaultTransactionContext()
            verify(søknadsbehandlingRepoMock).lagre(
                søknadsbehandling = argThat { it shouldBe underkjentMedNyOppgaveIdOgAttestering },
                sessionContext = anyOrNull(),
            )

            verify(behandlingMetricsMock).incrementUnderkjentCounter(argThat { it shouldBe BehandlingMetrics.UnderkjentHandlinger.PERSISTERT })
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe innvilgetBehandlingTilAttestering.oppgaveId })
            verify(observerMock).handle(
                argThat {
                    it shouldBe StatistikkEvent.Behandling.Søknad.Underkjent.Innvilget(
                        underkjentMedNyOppgaveIdOgAttestering,
                    )
                },
            )
        }

        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
        )
    }

    @Test
    fun `underkjenner behandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn innvilgetBehandlingTilAttestering
            on { hentForSak(any(), anyOrNull()) } doReturn emptyList()
        }

        val personServiceMock: PersonService = mock {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn nyOppgaveId.right()
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val behandlingMetricsMock = mock<BehandlingMetrics>()

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            behandlingMetrics = behandlingMetricsMock,
        ).underkjenn(
            SøknadsbehandlingService.UnderkjennRequest(
                behandlingId = innvilgetBehandlingTilAttestering.id,
                attestering = underkjentAttestering,
            ),
        )

        val underkjentMedNyOppgaveIdOgAttestering = UnderkjentSøknadsbehandling.Innvilget(
            id = innvilgetBehandlingTilAttestering.id,
            opprettet = innvilgetBehandlingTilAttestering.opprettet,
            sakId = innvilgetBehandlingTilAttestering.sakId,
            saksnummer = innvilgetBehandlingTilAttestering.saksnummer,
            søknad = innvilgetBehandlingTilAttestering.søknad,
            oppgaveId = nyOppgaveId,
            fnr = innvilgetBehandlingTilAttestering.fnr,
            beregning = innvilgetBehandlingTilAttestering.beregning,
            simulering = innvilgetBehandlingTilAttestering.simulering,
            saksbehandler = innvilgetBehandlingTilAttestering.saksbehandler,
            attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(underkjentAttestering),
            fritekstTilBrev = "",
            aldersvurdering = innvilgetBehandlingTilAttestering.aldersvurdering,
            grunnlagsdataOgVilkårsvurderinger = innvilgetBehandlingTilAttestering.grunnlagsdataOgVilkårsvurderinger,
            avkorting = AvkortingVedSøknadsbehandling.IngenAvkorting,
            sakstype = innvilgetBehandlingTilAttestering.sakstype,
            søknadsbehandlingsHistorikk = innvilgetBehandlingTilAttestering.søknadsbehandlingsHistorikk,
        )

        actual shouldBe underkjentMedNyOppgaveIdOgAttestering.right()

        inOrder(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
        ) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
            verify(oppgaveServiceMock).opprettOppgave(
                argThat {
                    it shouldBe oppgaveConfig
                },
            )
            verify(behandlingMetricsMock).incrementUnderkjentCounter(BehandlingMetrics.UnderkjentHandlinger.OPPRETTET_OPPGAVE)
            verify(søknadsbehandlingRepoMock).defaultTransactionContext()
            verify(søknadsbehandlingRepoMock).lagre(eq(underkjentMedNyOppgaveIdOgAttestering), anyOrNull())
            verify(behandlingMetricsMock).incrementUnderkjentCounter(BehandlingMetrics.UnderkjentHandlinger.PERSISTERT)
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe innvilgetBehandlingTilAttestering.oppgaveId })
            verify(behandlingMetricsMock).incrementUnderkjentCounter(BehandlingMetrics.UnderkjentHandlinger.LUKKET_OPPGAVE)
        }

        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
        )
    }
}
