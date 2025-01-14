package no.nav.su.se.bakover.service.dokument

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.DokumentRepo
import dokument.domain.Dokumentdistribusjon
import dokument.domain.KunneIkkeJournalføreOgDistribuereBrev
import dokument.domain.brev.KunneIkkeJournalføreBrev
import dokument.domain.brev.KunneIkkeJournalføreDokument
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.journalpost.JournalpostCommand
import no.nav.su.se.bakover.domain.journalpost.JournalpostForSakCommand
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.service.journalføring.JournalføringOgDistribueringsResultat
import no.nav.su.se.bakover.service.journalføring.JournalføringOgDistribueringsResultat.Companion.logResultat
import no.nav.su.se.bakover.service.journalføring.JournalføringOgDistribueringsResultat.Companion.tilResultat
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import person.domain.PersonService

/**
 * journalfører 'vanlige' dokumenter (f.eks vedtak). Ment å bli kallt fra en jobb
 */
class JournalførDokumentService(
    private val dokArkiv: DokArkiv,
    private val dokumentRepo: DokumentRepo,
    private val sakService: SakService,
    private val personService: PersonService,
) {
    val log: Logger = LoggerFactory.getLogger(this::class.java)

    fun journalfør(): List<JournalføringOgDistribueringsResultat> = dokumentRepo.hentDokumenterForJournalføring()
        .map { dokumentdistribusjon -> journalførDokument(dokumentdistribusjon).tilResultat(dokumentdistribusjon, log) }
        .also { it.logResultat("Journalfør dokument", log) }

    /**
     * Henter Person fra PersonService med systembruker.
     * Ment brukt fra async-operasjoner som ikke er knyttet til en bruker med token.
     */
    private fun journalførDokument(dokumentdistribusjon: Dokumentdistribusjon): Either<KunneIkkeJournalføreDokument, Dokumentdistribusjon> {
        val sakInfo = sakService.hentSakInfo(dokumentdistribusjon.dokument.metadata.sakId).getOrElse {
            throw IllegalStateException("Fant ikke sak. Her burde vi egentlig sak finnes. sakId ${dokumentdistribusjon.dokument.metadata.sakId}")
        }
        val person = personService.hentPersonMedSystembruker(sakInfo.fnr)
            .getOrElse { return KunneIkkeJournalføreDokument.KunneIkkeFinnePerson.left() }

        val journalførtDokument = dokumentdistribusjon.journalfør {
            journalfør(
                journalpost = JournalpostForSakCommand.Brev(
                    saksnummer = sakInfo.saksnummer,
                    dokument = dokumentdistribusjon.dokument,
                    sakstype = sakInfo.type,
                    fnr = person.ident.fnr,
                    navn = person.navn,
                ),
            ).mapLeft { KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.FeilVedJournalføring }
        }

        return journalførtDokument
            .mapLeft {
                when (it) {
                    is KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.AlleredeJournalført -> return dokumentdistribusjon.right()
                    KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.FeilVedJournalføring -> KunneIkkeJournalføreDokument.FeilVedOpprettelseAvJournalpost
                }
            }
            .onRight {
                dokumentRepo.oppdaterDokumentdistribusjon(it)
            }
    }

    private fun journalfør(journalpost: JournalpostCommand): Either<KunneIkkeJournalføreBrev, JournalpostId> {
        return dokArkiv.opprettJournalpost(journalpost)
            .mapLeft {
                log.error("Journalføring: Kunne ikke journalføre i eksternt system (joark/dokarkiv)")
                KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost
            }
    }
}
