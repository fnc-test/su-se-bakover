package no.nav.su.se.bakover.service.brev

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.JournalpostFactory
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordeling
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.brev.Brevdata
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

internal class BrevServiceImpl(
    private val pdfGenerator: PdfGenerator,
    private val personOppslag: PersonOppslag,
    private val dokArkiv: DokArkiv,
    private val dokDistFordeling: DokDistFordeling
) : BrevService {
    private val log = LoggerFactory.getLogger(this::class.java)

    private fun lagBrevInnhold(request: LagBrevRequest, person: Person): Brevdata {
        val personalia = lagPersonalia(person)
        return request.lagBrevdata(personalia)
    }

    private fun lagPersonalia(person: Person) = Brevdata.Personalia(
        dato = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
        fødselsnummer = person.ident.fnr,
        fornavn = person.navn.fornavn,
        etternavn = person.navn.etternavn,
        adresse = person.adresse?.adressenavn,
        bruksenhet = person.adresse?.bruksenhet,
        husnummer = person.adresse?.husnummer,
        postnummer = person.adresse?.poststed?.postnummer,
        poststed = person.adresse?.poststed?.poststed
    )

    override fun lagBrev(request: LagBrevRequest): Either<KunneIkkeLageBrev, ByteArray> {
        val person = hentPersonFraFnr(request.getFnr()).fold(
            { return KunneIkkeLageBrev.FantIkkePerson.left() },
            { it }
        )
        return lagPdf(lagBrevInnhold(request, person))
    }

    override fun journalførBrev(request: LagBrevRequest, sakId: UUID): Either<KunneIkkeJournalføreBrev, JournalpostId> {
        val person = hentPersonFraFnr(request.getFnr()).fold(
            { return KunneIkkeJournalføreBrev.FantIkkePerson.left() },
            { it }
        )
        val brevInnhold = lagBrevInnhold(request, person)
        val brevPdf = lagPdf(brevInnhold).fold(
            { return KunneIkkeJournalføreBrev.KunneIkkeGenereBrev.left() },
            { it }
        )

        return dokArkiv.opprettJournalpost(
            JournalpostFactory.lagJournalpost(
                person = person,
                sakId = sakId,
                brevdata = brevInnhold,
                pdf = brevPdf
            )
        ).mapLeft {
            log.error("Journalføring: Kunne ikke journalføre i ekstern system (joark/dokarkiv)")
            KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost
        }.map { it }
    }

    override fun distribuerBrev(journalpostId: JournalpostId): Either<KunneIkkeDistribuereBrev, String> =
        dokDistFordeling.bestillDistribusjon(journalpostId)
            .mapLeft {
                log.error("Feil ved bestilling av distribusjon for journalpostId:$journalpostId")
                KunneIkkeDistribuereBrev
            }

    private fun lagPdf(brevdata: Brevdata): Either<KunneIkkeLageBrev, ByteArray> {
        return pdfGenerator.genererPdf(brevdata)
            .mapLeft { KunneIkkeLageBrev.KunneIkkeGenererePDF }
            .map { it }
    }

    private fun hentPersonFraFnr(fnr: Fnr) = personOppslag.person(fnr)
        .mapLeft {
            log.error("Fant ikke person i eksternt system basert på sakens fødselsnummer.")
            it
        }.map {
            log.info("Hentet person fra eksternt system OK")
            it
        }
}
