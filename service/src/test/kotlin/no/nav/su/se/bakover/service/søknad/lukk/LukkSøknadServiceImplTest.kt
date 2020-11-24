package no.nav.su.se.bakover.service.søknad.lukk

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Person.Navn
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.Søknad.Lukket.LukketType.AVVIST
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.brev.BrevConfig
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.brev.søknad.lukk.AvvistSøknadBrevRequest
import no.nav.su.se.bakover.domain.brev.søknad.lukk.TrukketSøknadBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.PersonOppslag
import no.nav.su.se.bakover.domain.søknad.LukkSøknadRequest
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeDistribuereBrev
import no.nav.su.se.bakover.service.brev.KunneIkkeJournalføreBrev
import no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.sak.SakService
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

internal class LukkSøknadServiceImplTest {
    private val fixedEpochClock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
    private val sakId = UUID.randomUUID()
    private val saksbehandler = NavIdentBruker.Saksbehandler("Z993156")
    private val søknadInnhold = SøknadInnholdTestdataBuilder.build()
    private val nySøknadJournalpostId = JournalpostId("nySøknadJournalpostId")
    private val lukketJournalpostId = JournalpostId("lukketJournalpostId")
    private val brevbestillingId = BrevbestillingId("brevbestillingId")
    private val oppgaveId = OppgaveId("oppgaveId")
    private val fnr = søknadInnhold.personopplysninger.fnr
    private val person = Person(
        ident = Ident(
            fnr = fnr,
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
    private val sak = Sak(
        id = sakId,
        opprettet = Tidspunkt.EPOCH,
        fnr = fnr,
        søknader = emptyList(),
        behandlinger = emptyList(),
        oppdrag = Oppdrag(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId,
            utbetalinger = emptyList()
        )
    )
    private val nySøknad = Søknad.Ny(
        sakId = sakId,
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.EPOCH,
        søknadInnhold = søknadInnhold,
    )
    private val lukketSøknad = Søknad.Lukket(
        sakId = sakId,
        id = nySøknad.id,
        opprettet = nySøknad.opprettet,
        søknadInnhold = søknadInnhold,
        journalpostId = null,
        oppgaveId = null,
        lukketTidspunkt = Tidspunkt.EPOCH,
        lukketAv = saksbehandler,
        lukketType = Søknad.Lukket.LukketType.TRUKKET,
        lukketJournalpostId = null,
        lukketBrevbestillingId = null
    )

    private val journalførtSøknadMedOppgave = nySøknad
        .journalfør(nySøknadJournalpostId)
        .medOppgave(oppgaveId)

    private val trekkSøknadRequest = LukkSøknadRequest.MedBrev.TrekkSøknad(
        søknadId = nySøknad.id,
        saksbehandler = saksbehandler,
        trukketDato = 1.januar(2020)
    )

    @Test
    fun `fant ikke søknad`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn null
        }
        val sakServiceMock = mock<SakService>()
        val brevServiceMock = mock<BrevService>()
        val oppgaveServiceMock = mock<OppgaveService>()
        val personOppslagMock = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }

        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personOppslag = personOppslagMock,
            clock = fixedEpochClock,
        ).lukkSøknad(trekkSøknadRequest) shouldBe KunneIkkeLukkeSøknad.FantIkkeSøknad.left()

        verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personOppslagMock)
    }

    @Test
    fun `trekker en søknad uten mangler`() {

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
            on { oppdaterSøknad(any()) }.doNothing()
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn lukketJournalpostId.right()
            on { distribuerBrev(any()) } doReturn brevbestillingId.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val personOppslagMock = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }

        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personOppslag = personOppslagMock,
            clock = fixedEpochClock,
        ).lukkSøknad(trekkSøknadRequest) shouldBe LukketSøknad.UtenMangler(sak).right()

        inOrder(
            søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personOppslagMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(personOppslagMock).person(argThat { it shouldBe fnr })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe journalførtSøknadMedOppgave.id })

            verify(brevServiceMock).journalførBrev(
                request = argThat {
                    it shouldBe TrukketSøknadBrevRequest(
                        person = person,
                        søknad = lukketSøknad.copy(
                            journalpostId = nySøknadJournalpostId,
                            oppgaveId = oppgaveId,
                        ),
                        trukketDato = 1.januar(2020)
                    )
                },
                sakId = argThat { it shouldBe journalførtSøknadMedOppgave.sakId }
            )
            verify(brevServiceMock).distribuerBrev(argThat { it shouldBe lukketJournalpostId })
            verify(søknadRepoMock).oppdaterSøknad(
                argThat {
                    it shouldBe lukketSøknad.copy(
                        oppgaveId = oppgaveId,
                        journalpostId = nySøknadJournalpostId,
                        lukketJournalpostId = lukketJournalpostId,
                        lukketBrevbestillingId = brevbestillingId
                    )
                }
            )
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe journalførtSøknadMedOppgave.sakId })
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe oppgaveId })
        }
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personOppslagMock)
    }

    @Test
    fun `lukker avvist søknad uten brev`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
            on { oppdaterSøknad(any()) }.doNothing()
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }

        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }

        val brevServiceMock = mock<BrevService>()

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val personOppslagMock = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }

        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personOppslag = personOppslagMock,
            clock = fixedEpochClock,
        ).lukkSøknad(
            LukkSøknadRequest.UtenBrev.AvvistSøknad(
                søknadId = journalførtSøknadMedOppgave.id,
                saksbehandler = saksbehandler
            )
        ) shouldBe LukketSøknad.UtenMangler(sak).right()

        inOrder(
            søknadRepoMock, sakServiceMock, oppgaveServiceMock, personOppslagMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(personOppslagMock).person(argThat { it shouldBe fnr })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(søknadRepoMock).oppdaterSøknad(
                argThat {
                    it shouldBe lukketSøknad.copy(
                        journalpostId = nySøknadJournalpostId,
                        oppgaveId = oppgaveId,
                        lukketType = AVVIST,
                    )
                },
            )
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe journalførtSøknadMedOppgave.sakId })
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe oppgaveId })
        }
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personOppslagMock)
    }

    @Test
    fun `lukker avvist søknad med brev`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
            on { oppdaterSøknad(any()) }.doNothing()
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }

        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }

        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn lukketJournalpostId.right()
            on { distribuerBrev(any()) } doReturn brevbestillingId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val personOppslagMock = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }
        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personOppslag = personOppslagMock,
            clock = fixedEpochClock,
        ).lukkSøknad(
            LukkSøknadRequest.MedBrev.AvvistSøknad(
                søknadId = journalførtSøknadMedOppgave.id,
                saksbehandler = saksbehandler,
                brevConfig = BrevConfig.Fritekst("Fritekst")
            )
        ) shouldBe LukketSøknad.UtenMangler(sak).right()

        inOrder(
            søknadRepoMock, sakServiceMock, oppgaveServiceMock, brevServiceMock, personOppslagMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(personOppslagMock).person(argThat { it shouldBe fnr })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(brevServiceMock).journalførBrev(
                argThat {
                    it shouldBe AvvistSøknadBrevRequest(
                        person = person,
                        BrevConfig.Fritekst("Fritekst")
                    )
                },
                argThat { it shouldBe nySøknad.sakId }
            )
            verify(brevServiceMock).distribuerBrev(argThat { it shouldBe lukketJournalpostId })
            verify(søknadRepoMock).oppdaterSøknad(
                argThat {
                    it shouldBe lukketSøknad.copy(
                        journalpostId = nySøknadJournalpostId,
                        oppgaveId = oppgaveId,
                        lukketType = AVVIST,
                        lukketJournalpostId = lukketJournalpostId,
                        lukketBrevbestillingId = brevbestillingId
                    )
                },
            )
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe journalførtSøknadMedOppgave.sakId })
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe oppgaveId })
        }
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personOppslagMock)
    }

    @Test
    fun `en søknad kan ikke trekkes før den er opprettet`() {

        val treDagerGammelSøknad = Søknad.Ny(
            sakId = sakId,
            id = UUID.randomUUID(),
            opprettet = LocalDateTime.now().minusDays(3).toTidspunkt(zoneIdOslo),
            søknadInnhold = søknadInnhold,
        )

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn treDagerGammelSøknad
            on { oppdaterSøknad(any()) }.doNothing()
            on { harSøknadPåbegyntBehandling(any()) } doReturn true
        }
        val sakServiceMock = mock<SakService>()
        val brevServiceMock = mock<BrevService>()
        val oppgaveServiceMock = mock<OppgaveService>()
        val personOppslagMock = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }

        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personOppslag = personOppslagMock,
            clock = fixedEpochClock,
        ).lukkSøknad(
            LukkSøknadRequest.MedBrev.TrekkSøknad(
                søknadId = treDagerGammelSøknad.id,
                saksbehandler = saksbehandler,
                trukketDato = LocalDate.now().minusDays(4)
            )
        ) shouldBe KunneIkkeLukkeSøknad.UgyldigDato.left()

        inOrder(
            søknadRepoMock, sakServiceMock, brevServiceMock, personOppslagMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe treDagerGammelSøknad.id })
            verify(personOppslagMock).person(argThat { it shouldBe fnr })
        }

        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personOppslagMock)
    }

    @Test
    fun `en søknad med behandling skal ikke bli trukket`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn nySøknad
            on { oppdaterSøknad(any()) }.doNothing()
            on { harSøknadPåbegyntBehandling(any()) } doReturn true
        }
        val sakServiceMock = mock<SakService>()
        val brevServiceMock = mock<BrevService>()
        val oppgaveServiceMock = mock<OppgaveService>()

        val personOppslagMock = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }
        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personOppslag = personOppslagMock,
            clock = fixedEpochClock,
        ).lukkSøknad(
            LukkSøknadRequest.UtenBrev.BortfaltSøknad(
                søknadId = nySøknad.id,
                saksbehandler = saksbehandler
            )
        ) shouldBe KunneIkkeLukkeSøknad.SøknadHarEnBehandling.left()

        inOrder(
            søknadRepoMock, sakServiceMock, brevServiceMock, personOppslagMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe nySøknad.id })
            verify(personOppslagMock).person(argThat { it shouldBe fnr })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe nySøknad.id })
        }

        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personOppslagMock)
    }

    @Test
    fun `en allerede trukket søknad skal ikke bli trukket`() {
        val trukketSøknad = lukketSøknad.copy()
        val trekkSøknadRequest = trekkSøknadRequest.copy(
            søknadId = trukketSøknad.id
        )

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn trukketSøknad
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val sakServiceMock = mock<SakService>()

        val brevServiceMock = mock<BrevService>()
        val personOppslagMock = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }

        val oppgaveServiceMock = mock<OppgaveService>()
        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personOppslag = personOppslagMock,
            clock = fixedEpochClock,
        ).lukkSøknad(trekkSøknadRequest) shouldBe KunneIkkeLukkeSøknad.SøknadErAlleredeLukket.left()

        inOrder(søknadRepoMock, personOppslagMock) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe trukketSøknad.id })
            verify(personOppslagMock).person(argThat { it shouldBe fnr })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe trukketSøknad.id })
        }
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personOppslagMock)
    }

    @Test
    fun `lager brevutkast`() {
        val pdf = "".toByteArray()
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn nySøknad
        }
        val sakServiceMock = mock<SakService>()
        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn pdf.right()
        }

        val oppgaveServiceMock = mock<OppgaveService>()

        val personOppslagMock = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }

        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personOppslag = personOppslagMock,
            clock = fixedEpochClock,
        ).lagBrevutkast(trekkSøknadRequest) shouldBe pdf.right()

        inOrder(
            søknadRepoMock, brevServiceMock, personOppslagMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe nySøknad.id })
            verify(personOppslagMock).person(argThat { it shouldBe fnr })

            verify(brevServiceMock).lagBrev(
                argThat {
                    it shouldBe TrukketSøknadBrevRequest(person, nySøknad, 1.januar(2020))
                }
            )
        }

        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personOppslagMock)
    }

    @Test
    fun `lager brevutkast finner ikke søknad`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn null
        }
        val sakServiceMock = mock<SakService>()
        val brevServiceMock = mock<BrevService>()
        val oppgaveServiceMock = mock<OppgaveService>()
        val personOppslagMock = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }

        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personOppslag = personOppslagMock,
            clock = fixedEpochClock,
        ).lagBrevutkast(trekkSøknadRequest) shouldBe KunneIkkeLageBrevutkast.FantIkkeSøknad.left()

        inOrder(
            søknadRepoMock, brevServiceMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe nySøknad.id })
        }

        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personOppslagMock)
    }

    @Test
    fun `lager brevutkast klarer ikke lage brev`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn nySøknad
        }
        val sakServiceMock = mock<SakService>()
        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn KunneIkkeLageBrev.KunneIkkeGenererePDF.left()
        }

        val oppgaveServiceMock = mock<OppgaveService>()

        val personOppslagMock = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }
        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personOppslag = personOppslagMock,
            clock = fixedEpochClock,
        ).lagBrevutkast(trekkSøknadRequest) shouldBe KunneIkkeLageBrevutkast.KunneIkkeLageBrev.left()

        inOrder(
            søknadRepoMock, brevServiceMock, personOppslagMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe nySøknad.id })
            verify(personOppslagMock).person(argThat { it shouldBe fnr })

            verify(brevServiceMock).lagBrev(
                argThat {
                    it shouldBe TrukketSøknadBrevRequest(person, nySøknad, 1.januar(2020))
                }
            )
        }

        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personOppslagMock)
    }

    @Test
    fun `svarer med ukjentBrevType når det ikke skal lages brev`() {

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn nySøknad
        }
        val sakServiceMock = mock<SakService>()

        val brevServiceMock = mock<BrevService>()

        val oppgaveServiceMock = mock<OppgaveService>()

        val personOppslagMock = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }
        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personOppslag = personOppslagMock,
            clock = fixedEpochClock,
        ).lagBrevutkast(
            LukkSøknadRequest.UtenBrev.BortfaltSøknad(
                søknadId = nySøknad.id,
                saksbehandler = saksbehandler
            )
        ) shouldBe KunneIkkeLageBrevutkast.UkjentBrevtype.left()

        inOrder(
            søknadRepoMock, sakServiceMock, brevServiceMock, personOppslagMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe nySøknad.id })
            verify(personOppslagMock).person(argThat { it shouldBe fnr })
        }

        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personOppslagMock)
    }

    @Test
    fun `Kan ikke sende brevbestilling og kan ikke lukke oppgave`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
            on { oppdaterSøknad(any()) }.doNothing()
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn lukketJournalpostId.right()
            on { distribuerBrev(any()) } doReturn KunneIkkeDistribuereBrev.left()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn KunneIkkeLukkeOppgave.left()
        }

        val personOppslagMock = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }

        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personOppslag = personOppslagMock,
            clock = fixedEpochClock,
        ).lukkSøknad(trekkSøknadRequest) shouldBe LukketSøknad.MedMangler.KunneIkkeDistribuereBrev(sak).right()

        inOrder(
            søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personOppslagMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe nySøknad.id })
            verify(personOppslagMock).person(argThat { it shouldBe fnr })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe nySøknad.id })
            verify(brevServiceMock).journalførBrev(
                argThat {
                    it shouldBe TrukketSøknadBrevRequest(
                        person = person,
                        søknad = lukketSøknad.copy(
                            lukketJournalpostId = null,
                            journalpostId = nySøknadJournalpostId,
                            oppgaveId = oppgaveId
                        ),
                        trukketDato = 1.januar(2020)
                    )
                },
                argThat { it shouldBe nySøknad.sakId }
            )
            verify(brevServiceMock).distribuerBrev(argThat { it shouldBe lukketJournalpostId })
            verify(søknadRepoMock).oppdaterSøknad(
                argThat {
                    it shouldBe lukketSøknad.copy(
                        lukketJournalpostId = lukketJournalpostId,
                        lukketBrevbestillingId = null,
                        journalpostId = nySøknadJournalpostId,
                        oppgaveId = oppgaveId
                    )
                }
            )

            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe nySøknad.sakId })
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe oppgaveId })
        }
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personOppslagMock)
    }

    @Test
    fun `lukker journalført søknad uten oppgave`() {
        val søknad = Søknad.Journalført.UtenOppgave(
            sakId = nySøknad.sakId,
            id = nySøknad.id,
            opprettet = nySøknad.opprettet,
            søknadInnhold = nySøknad.søknadInnhold,
            journalpostId = nySøknadJournalpostId,
        )

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn søknad
            on { oppdaterSøknad(any()) }.doNothing()
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn lukketJournalpostId.right()
            on { distribuerBrev(lukketJournalpostId) } doReturn brevbestillingId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService>()

        val personOppslagMock = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }
        val actual = LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personOppslag = personOppslagMock,
            clock = fixedEpochClock,
        ).lukkSøknad(trekkSøknadRequest)

        actual shouldBe LukketSøknad.UtenMangler(sak).right()

        inOrder(
            søknadRepoMock, sakServiceMock, brevServiceMock, personOppslagMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe søknad.id })
            verify(personOppslagMock).person(argThat { it shouldBe fnr })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe søknad.id })
            verify(brevServiceMock).journalførBrev(
                request = argThat {
                    it shouldBe TrukketSøknadBrevRequest(
                        person = person,
                        søknad = lukketSøknad.copy(
                            journalpostId = nySøknadJournalpostId,
                        ),
                        trukketDato = 1.januar(2020)
                    )
                },
                sakId = argThat {
                    it shouldBe søknad.sakId
                }
            )
            verify(brevServiceMock).distribuerBrev(argThat { it shouldBe lukketJournalpostId })
            verify(søknadRepoMock).oppdaterSøknad(
                argThat {
                    it shouldBe lukketSøknad.copy(
                        journalpostId = nySøknadJournalpostId,
                        lukketJournalpostId = lukketJournalpostId,
                        lukketBrevbestillingId = brevbestillingId
                    )
                },
            )
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe søknad.sakId })
        }
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personOppslagMock)
    }

    @Test
    fun `lukker søknad uten journalføring og oppgave`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn nySøknad
            on { oppdaterSøknad(any()) }.doNothing()
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn lukketJournalpostId.right()
            on { distribuerBrev(lukketJournalpostId) } doReturn brevbestillingId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService>()

        val personOppslagMock = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }
        val actual = LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personOppslag = personOppslagMock,
            clock = fixedEpochClock,
        ).lukkSøknad(trekkSøknadRequest)

        actual shouldBe LukketSøknad.UtenMangler(sak).right()

        inOrder(
            søknadRepoMock, sakServiceMock, brevServiceMock, personOppslagMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe nySøknad.id })
            verify(personOppslagMock).person(argThat { it shouldBe fnr })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe nySøknad.id })
            verify(brevServiceMock).journalførBrev(
                request = argThat {
                    it shouldBe TrukketSøknadBrevRequest(
                        person = person,
                        søknad = lukketSøknad,
                        trukketDato = 1.januar(2020),
                    )
                },
                sakId = argThat {
                    it shouldBe nySøknad.sakId
                }
            )
            verify(brevServiceMock).distribuerBrev(argThat { it shouldBe lukketJournalpostId })
            verify(søknadRepoMock).oppdaterSøknad(
                argThat {
                    it shouldBe lukketSøknad.copy(
                        lukketJournalpostId = lukketJournalpostId,
                        lukketBrevbestillingId = brevbestillingId
                    )
                },
            )
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe nySøknad.sakId })
        }
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personOppslagMock)
    }

    @Test
    fun `Kan ikke lukke oppgave`() {

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
            on { oppdaterSøknad(any()) }.doNothing()
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn lukketJournalpostId.right()
            on { distribuerBrev(any()) } doReturn brevbestillingId.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn KunneIkkeLukkeOppgave.left()
        }
        val personOppslagMock = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }

        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personOppslag = personOppslagMock,
            clock = fixedEpochClock,
        ).lukkSøknad(trekkSøknadRequest) shouldBe LukketSøknad.MedMangler.KunneIkkeLukkeOppgave(sak).right()

        inOrder(
            søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personOppslagMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(personOppslagMock).person(argThat { it shouldBe fnr })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe journalførtSøknadMedOppgave.id })

            verify(brevServiceMock).journalførBrev(
                request = argThat {
                    it shouldBe TrukketSøknadBrevRequest(
                        person = person,
                        søknad = lukketSøknad.copy(
                            journalpostId = nySøknadJournalpostId,
                            oppgaveId = oppgaveId,
                        ),
                        trukketDato = 1.januar(2020),
                    )
                },
                sakId = argThat { it shouldBe journalførtSøknadMedOppgave.sakId }
            )
            verify(brevServiceMock).distribuerBrev(argThat { it shouldBe lukketJournalpostId })
            verify(søknadRepoMock).oppdaterSøknad(
                argThat {
                    it shouldBe lukketSøknad.copy(
                        oppgaveId = oppgaveId,
                        journalpostId = nySøknadJournalpostId,
                        lukketJournalpostId = lukketJournalpostId,
                        lukketBrevbestillingId = brevbestillingId
                    )
                }
            )
            verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe journalførtSøknadMedOppgave.sakId })
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe oppgaveId })
        }
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personOppslagMock)
    }

    @Test
    fun `Kan ikke journalføre`() {

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(any()) } doReturn journalførtSøknadMedOppgave
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val sakServiceMock = mock<SakService>()
        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost.left()
        }
        val oppgaveServiceMock = mock<OppgaveService>()

        val personOppslagMock = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }

        LukkSøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personOppslag = personOppslagMock,
            clock = fixedEpochClock,
        ).lukkSøknad(trekkSøknadRequest) shouldBe KunneIkkeLukkeSøknad.KunneIkkeJournalføreBrev.left()

        inOrder(
            søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personOppslagMock
        ) {
            verify(søknadRepoMock).hentSøknad(argThat { it shouldBe journalførtSøknadMedOppgave.id })
            verify(personOppslagMock).person(argThat { it shouldBe fnr })
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(argThat { it shouldBe journalførtSøknadMedOppgave.id })

            verify(brevServiceMock).journalførBrev(
                request = argThat {
                    it shouldBe TrukketSøknadBrevRequest(
                        person = person,
                        søknad = lukketSøknad.copy(
                            journalpostId = nySøknadJournalpostId,
                            oppgaveId = oppgaveId,
                        ),
                        trukketDato = 1.januar(2020)
                    )
                },
                sakId = argThat { it shouldBe journalførtSøknadMedOppgave.sakId }
            )
        }
        verifyNoMoreInteractions(søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveServiceMock, personOppslagMock)
    }
}
