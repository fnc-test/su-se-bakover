package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.avslag.Avslag
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.søknadsbehandling.Statusovergang
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.behandling.KunneIkkeIverksetteBehandling
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.statistikk.EventObserver
import org.slf4j.LoggerFactory
import java.time.Clock

class IverksettSøknadsbehandlingService(
    private val oppgaveService: OppgaveService,
    private val personService: PersonService,
    private val behandlingMetrics: BehandlingMetrics,
    private val clock: Clock,
    private val microsoftGraphApiClient: MicrosoftGraphApiOppslag,
    private val brevService: BrevService,
) {
    private val observers: MutableList<EventObserver> = mutableListOf()

    fun addObserver(observer: EventObserver) {
        observers.add(observer)
    }

    private val log = LoggerFactory.getLogger(this::class.java)

    internal fun opprettJournalpostForAvslag(
        søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag,
        attestant: NavIdentBruker.Attestant,
    ): Either<Statusovergang.KunneIkkeIverksetteSøknadsbehandling, JournalpostId> {
        val person: Person = personService.hentPerson(søknadsbehandling.fnr).getOrElse {
            log.error("Kunne ikke iverksette behandling; fant ikke person")
            return Statusovergang.KunneIkkeIverksetteSøknadsbehandling.FantIkkePerson.left()
        }
        val saksbehandlerNavn = hentNavnForNavIdent(søknadsbehandling.saksbehandler).getOrHandle {
            return Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeJournalføre.left()
        }
        val attestantNavn = hentNavnForNavIdent(attestant).getOrHandle {
            return Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeJournalføre.left()
        }

        return brevService.journalførBrev(
            request = AvslagBrevRequest(
                person = person,
                avslag = Avslag(
                    opprettet = Tidspunkt.now(clock),
                    avslagsgrunner = søknadsbehandling.avslagsgrunner,
                    harEktefelle = søknadsbehandling.behandlingsinformasjon.harEktefelle(),
                    beregning = if (søknadsbehandling is Søknadsbehandling.TilAttestering.Avslag.MedBeregning) søknadsbehandling.beregning else null
                ),
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn
            ),
            saksnummer = søknadsbehandling.saksnummer,
        ).mapLeft {
            Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeJournalføre
        }.map {
            behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.JOURNALFØRT)
            it
        }
    }

    private fun hentNavnForNavIdent(navIdent: NavIdentBruker): Either<KunneIkkeIverksetteBehandling.FikkIkkeHentetSaksbehandlerEllerAttestant, String> {
        return microsoftGraphApiClient.hentBrukerinformasjonForNavIdent(navIdent)
            .mapLeft { KunneIkkeIverksetteBehandling.FikkIkkeHentetSaksbehandlerEllerAttestant }
            .map { it.displayName }
    }

    internal fun distribuerBrevOgLukkOppgaveForAvslag(
        søknadsbehandlingUtenBrev: Søknadsbehandling.Iverksatt.Avslag,
    ): Søknadsbehandling.Iverksatt.Avslag {

        val brevResultat: Either<Søknadsbehandling.Iverksatt.KunneIkkeDistribuereBrev, Søknadsbehandling.Iverksatt.Avslag> =
            søknadsbehandlingUtenBrev.distribuerBrev { journalpostId ->
                brevService.distribuerBrev(journalpostId)
                    .mapLeft {
                        Søknadsbehandling.Iverksatt.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev(journalpostId)
                    }.map {
                        behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.DISTRIBUERT_BREV)
                        it
                    }
            }

        oppgaveService.lukkOppgave(søknadsbehandlingUtenBrev.oppgaveId)
            .mapLeft {
                log.error("Kunne ikke lukke oppgave ved avslag for behandling ${søknadsbehandlingUtenBrev.id}. Dette må gjøres manuelt.")
            }
            .map {
                log.info("Lukket oppgave ${søknadsbehandlingUtenBrev.oppgaveId} ved avslag for behandling ${søknadsbehandlingUtenBrev.id}")
                // TODO jah: Vurder behandling.oppdaterOppgaveId(null), men den kan ikke være null atm.
                behandlingMetrics.incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.LUKKET_OPPGAVE)
            }

        // TODO jah: implement version for søknadsbehandling
        // opprettVedtakssnapshotService.opprettVedtak(
        //     vedtakssnapshot = Vedtakssnapshot.Avslag.createFromBehandling(søknadsbehandlingUtenBrev, avslag.avslagsgrunner)
        // )

        return brevResultat.getOrElse { søknadsbehandlingUtenBrev }
    }
}
