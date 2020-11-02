package no.nav.su.se.bakover.service.søknad.lukk

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.brev.søknad.lukk.TrukketSøknadBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknad.LukkSøknadRequest
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeDistribuereBrev
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.sak.SakService
import org.junit.jupiter.api.Test
import java.util.UUID

internal class LukkSøknadServiceImplTest {
    private val sakId = UUID.randomUUID()
    private val saksbehandler = NavIdentBruker.Saksbehandler("Z993156")
    private val sak = Sak(
        id = sakId,
        opprettet = Tidspunkt.now(),
        fnr = Fnr("12345678901"),
        søknader = emptyList(),
        behandlinger = emptyList(),
        oppdrag = Oppdrag(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(),
            sakId = sakId,
            utbetalinger = emptyList()
        )
    )
    private val søknad = Søknad(
        sakId = sakId,
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(),
        søknadInnhold = SøknadInnholdTestdataBuilder.build(),
        lukket = null
    )
    private val trekkSøknadRequest = LukkSøknadRequest.MedBrev.TrekkSøknad(
        søknadId = søknad.id,
        saksbehandler = saksbehandler,
        trukketDato = 1.januar(2020)
    )
    private val oppgaveId = OppgaveId("1234")

    @Test
    fun `trekker en søknad og håndterer brev`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(søknad.id) } doReturn søknad
            on {
                lukkSøknad(
                    søknad.id,
                    Søknad.Lukket(tidspunkt = now(), saksbehandler.navIdent, type = Søknad.LukketType.TRUKKET)
                )
            }.doNothing()
            on { harSøknadPåbegyntBehandling(søknad.id) } doReturn false
            on { hentOppgaveId(søknad.id) } doReturn oppgaveId
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(søknad.sakId) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on {
                journalførBrev(
                    TrukketSøknadBrevRequest(søknad, 1.januar(2020)),
                    sak.id
                )
            } doReturn JournalpostId("en id").right()

            on { distribuerBrev(JournalpostId("en id")) } doReturn "en bestillings id".right()
        }
        val oppgaveClientMock = mock<OppgaveClient> {
            on { lukkOppgave(oppgaveId) } doReturn Unit.right()
        }

        createService(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveClient = oppgaveClientMock
        ).lukkSøknad(trekkSøknadRequest) shouldBe sak.right()

        inOrder(
            søknadRepoMock, sakServiceMock, brevServiceMock, oppgaveClientMock
        ) {
            verify(søknadRepoMock).hentSøknad(søknad.id)
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(søknad.id)
            verify(søknadRepoMock).lukkSøknad(
                argThat { it shouldBe søknad.id },
                argThat {
                    it.saksbehandler shouldBe saksbehandler.toString()
                    it.type shouldBe Søknad.LukketType.TRUKKET
                }
            )
            verify(brevServiceMock).journalførBrev(TrukketSøknadBrevRequest(søknad, 1.januar(2020)), søknad.sakId)
            verify(brevServiceMock).distribuerBrev(JournalpostId("en id"))
            verify(sakServiceMock).hentSak(søknad.sakId)
            verify(søknadRepoMock).hentOppgaveId(søknad.id)
            verify(oppgaveClientMock).lukkOppgave(oppgaveId)
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `lukker søknad hvor brev ikke skal sendes`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(søknad.id) } doReturn søknad
            on {
                lukkSøknad(
                    søknad.id,
                    Søknad.Lukket(tidspunkt = now(), saksbehandler.navIdent, type = Søknad.LukketType.AVVIST)
                )
            }.doNothing()
            on { harSøknadPåbegyntBehandling(søknad.id) } doReturn false
            on { hentOppgaveId(søknad.id) } doReturn oppgaveId
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(søknad.sakId) } doReturn sak.right()
        }
        val oppgaveClientMock = mock<OppgaveClient> {
            on { lukkOppgave(oppgaveId) } doReturn Unit.right()
        }

        createService(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            oppgaveClient = oppgaveClientMock
        ).lukkSøknad(
            LukkSøknadRequest.UtenBrev.AvvistSøknad(
                søknadId = søknad.id,
                saksbehandler = saksbehandler
            )
        ) shouldBe sak.right()

        inOrder(
            søknadRepoMock, sakServiceMock, oppgaveClientMock
        ) {
            verify(søknadRepoMock).hentSøknad(søknad.id)
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(søknad.id)
            verify(søknadRepoMock).lukkSøknad(
                argThat { it shouldBe søknad.id },
                argThat {
                    it.saksbehandler shouldBe saksbehandler.toString()
                    it.type shouldBe Søknad.LukketType.AVVIST
                }
            )
            verify(sakServiceMock).hentSak(søknad.sakId)
            verify(søknadRepoMock).hentOppgaveId(søknad.id)
            verify(oppgaveClientMock).lukkOppgave(oppgaveId)
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `en søknad med behandling skal ikke bli trukket`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(søknadId = søknad.id) } doReturn søknad
            on {
                lukkSøknad(
                    søknad.id,
                    Søknad.Lukket(tidspunkt = now(), saksbehandler.navIdent, type = Søknad.LukketType.BORTFALT)
                )
            }.doNothing()
            on { harSøknadPåbegyntBehandling(søknad.id) } doReturn true
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId = søknad.sakId) } doReturn sak.right()
        }
        createService(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
        ).lukkSøknad(
            LukkSøknadRequest.UtenBrev.BortfaltSøknad(
                søknadId = søknad.id,
                saksbehandler = saksbehandler
            )
        ) shouldBe KunneIkkeLukkeSøknad.SøknadHarEnBehandling.left()
    }

    @Test
    fun `en allerede trukket søknad skal ikke bli trukket`() {
        val søknad = Søknad(
            sakId = sakId,
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            lukket = Søknad.Lukket(
                tidspunkt = Tidspunkt.now(),
                saksbehandler = saksbehandler.navIdent,
                type = Søknad.LukketType.TRUKKET
            )
        )
        val trekkSøknadRequest = trekkSøknadRequest.copy(
            søknadId = søknad.id
        )

        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(søknadId = søknad.id) } doReturn søknad
            on {
                lukkSøknad(
                    søknad.id,
                    Søknad.Lukket(
                        tidspunkt = now(),
                        saksbehandler = saksbehandler.navIdent,
                        type = Søknad.LukketType.TRUKKET
                    )
                )
            }.doNothing()
            on { harSøknadPåbegyntBehandling(søknad.id) } doReturn false
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId = søknad.sakId) } doReturn sak.right()
        }
        createService(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
        ).lukkSøknad(trekkSøknadRequest) shouldBe KunneIkkeLukkeSøknad.SøknadErAlleredeLukket.left()
    }

    @Test
    fun `lager brevutkast`() {
        val pdf = "".toByteArray()
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(søknad.id) } doReturn søknad
        }
        val brevServiceMock = mock<BrevService> {
            on { lagBrev(TrukketSøknadBrevRequest(søknad, 1.januar(2020))) } doReturn pdf.right()
        }

        createService(
            søknadRepo = søknadRepoMock,
            brevService = brevServiceMock
        ).lagBrevutkast(trekkSøknadRequest) shouldBe pdf.right()
    }

    @Test
    fun `svarer med ukjentBrevType når det ikke skal lages brev`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(søknad.id) } doReturn søknad
        }
        createService(
            søknadRepo = søknadRepoMock
        ).lagBrevutkast(
            LukkSøknadRequest.UtenBrev.BortfaltSøknad(
                søknadId = søknad.id,
                saksbehandler = saksbehandler
            )
        ) shouldBe KunneIkkeLageBrevutkast.UkjentBrevtype.left()
    }

    @Test
    fun `svarer med feilmelding dersom jorunalføring og eller distribusjon av brev feiler`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(søknad.id) } doReturn søknad
            on {
                lukkSøknad(
                    søknad.id,
                    Søknad.Lukket(tidspunkt = now(), saksbehandler.navIdent, type = Søknad.LukketType.TRUKKET)
                )
            }.doNothing()
            on { harSøknadPåbegyntBehandling(søknad.id) } doReturn false
            on { hentOppgaveId(søknad.id) } doReturn oppgaveId
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(søknad.sakId) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on {
                journalførBrev(
                    TrukketSøknadBrevRequest(søknad, 1.januar(2020)),
                    sak.id
                )
            } doReturn JournalpostId("en id").right()

            on { distribuerBrev(JournalpostId("en id")) } doReturn KunneIkkeDistribuereBrev.left()
        }

        createService(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock
        ).lukkSøknad(trekkSøknadRequest) shouldBe KunneIkkeLukkeSøknad.KunneIkkeDistribuereBrev.left()

        inOrder(
            søknadRepoMock, sakServiceMock, brevServiceMock
        ) {
            verify(søknadRepoMock).hentSøknad(søknad.id)
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(søknad.id)
            verify(brevServiceMock).journalførBrev(TrukketSøknadBrevRequest(søknad, 1.januar(2020)), søknad.sakId)
            verify(brevServiceMock).distribuerBrev(JournalpostId("en id"))
            verify(søknadRepoMock).hentOppgaveId(søknad.id)
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `lukker ikke selve oppgaven hvis oppgaveclient returnerer null`() {
        val søknadRepoMock = mock<SøknadRepo> {
            on { hentSøknad(søknad.id) } doReturn søknad
            on {
                lukkSøknad(
                    søknad.id,
                    Søknad.Lukket(tidspunkt = now(), saksbehandler.navIdent, type = Søknad.LukketType.TRUKKET)
                )
            }.doNothing()
            on { harSøknadPåbegyntBehandling(søknad.id) } doReturn false
            on { hentOppgaveId(søknad.id) } doReturn null
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(søknad.sakId) } doReturn sak.right()
        }
        val brevServiceMock = mock<BrevService> {
            on {
                journalførBrev(
                    TrukketSøknadBrevRequest(søknad, 1.januar(2020)),
                    sak.id
                )
            } doReturn JournalpostId("en id").right()

            on { distribuerBrev(JournalpostId("en id")) } doReturn "en bestillings id".right()
        }

        createService(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
        ).lukkSøknad(trekkSøknadRequest) shouldBe sak.right()

        inOrder(
            søknadRepoMock, sakServiceMock, brevServiceMock
        ) {
            verify(søknadRepoMock).hentSøknad(søknad.id)
            verify(søknadRepoMock).harSøknadPåbegyntBehandling(søknad.id)
            verify(søknadRepoMock).lukkSøknad(
                argThat { it shouldBe søknad.id },
                argThat {
                    it.saksbehandler shouldBe saksbehandler.toString()
                    it.type shouldBe Søknad.LukketType.TRUKKET
                }
            )
            verify(brevServiceMock).journalførBrev(TrukketSøknadBrevRequest(søknad, 1.januar(2020)), søknad.sakId)
            verify(brevServiceMock).distribuerBrev(JournalpostId("en id"))
            verify(sakServiceMock).hentSak(søknad.sakId)
            verify(søknadRepoMock).hentOppgaveId(søknad.id)
            verifyNoMoreInteractions()
        }
    }

    private fun createService(
        søknadRepo: SøknadRepo = mock(),
        sakService: SakService = mock(),
        brevService: BrevService = mock(),
        oppgaveClient: OppgaveClient = mock()
    ) = LukkSøknadServiceImpl(
        søknadRepo = søknadRepo,
        sakService = sakService,
        brevService = brevService,
        oppgaveClient = oppgaveClient
    )
}
