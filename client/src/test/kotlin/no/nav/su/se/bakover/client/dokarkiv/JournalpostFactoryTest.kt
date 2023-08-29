package no.nav.su.se.bakover.client.dokarkiv

import dokument.domain.Dokument
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.person.Ident
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.pdfATom
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.Base64
import java.util.UUID
import kotlin.random.Random

internal class JournalpostFactoryTest {

    private val personMock = mock<Person> {
        on { ident } doReturn Ident(Fnr("12345678910"), AktørId("12345"))
        on { navn } doReturn Person.Navn("fornavn", "mellomnavn", "etternavn")
    }
    private val saksnummer = Saksnummer(Random.nextLong(2021, Long.MAX_VALUE))

    @Test
    fun `lager vedtakspost for vedtak dokumentkategori vedtak`() {
        val dokument = Dokument.MedMetadata.Vedtak(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            tittel = "tittel",
            generertDokument = pdfATom(),
            generertDokumentJson = """{"k":"v"}""",
            metadata = Dokument.Metadata(sakId = UUID.randomUUID()),
        )

        JournalpostFactory.lagJournalpost(personMock, saksnummer, dokument, Sakstype.UFØRE).let {
            it.shouldBeTypeOf<JournalpostForSak.Vedtakspost>()
            assert(it, dokument)
        }
    }

    @Test
    fun `lager infopost for dokumentkategori informasjon`() {
        val dokument = Dokument.MedMetadata.Informasjon.Annet(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            tittel = "tittel",
            generertDokument = pdfATom(),
            generertDokumentJson = """{"k":"v"}""",
            metadata = Dokument.Metadata(sakId = UUID.randomUUID()),
        )

        JournalpostFactory.lagJournalpost(personMock, saksnummer, dokument, Sakstype.UFØRE).let {
            it.shouldBeTypeOf<JournalpostForSak.Info>()
            assert(it, dokument)
        }
    }

    private fun assert(journalpost: Journalpost, dokument: Dokument) =
        assertJournalpost(journalpost, dokument.tittel, dokument.generertDokumentJson)

    private fun assertJournalpost(
        journalpost: Journalpost,
        tittel: String,
        originalJson: String,
    ) {
        journalpost.tittel shouldBe tittel
        journalpost.avsenderMottaker shouldBe AvsenderMottaker(
            id = personMock.ident.fnr.toString(),
            navn = "${personMock.navn.etternavn}, ${personMock.navn.fornavn} ${personMock.navn.mellomnavn}",
        )
        journalpost.behandlingstema shouldBe "ab0431"
        journalpost.tema shouldBe "SUP"
        journalpost.bruker shouldBe Bruker(id = personMock.ident.fnr.toString())
        journalpost.kanal shouldBe null
        journalpost.journalfoerendeEnhet shouldBe JournalførendeEnhet.ÅLESUND
        journalpost.journalpostType shouldBe JournalPostType.UTGAAENDE
        journalpost.sak shouldBe Fagsak(saksnummer.nummer.toString())
        journalpost.dokumenter shouldBe listOf(
            JournalpostDokument(
                tittel = tittel,
                dokumentvarianter = listOf(
                    DokumentVariant.ArkivPDF(fysiskDokument = Base64.getEncoder().encodeToString(pdfATom().getContent())),
                    DokumentVariant.OriginalJson(
                        fysiskDokument = Base64.getEncoder().encodeToString(originalJson.toByteArray()),
                    ),
                ),
            ),
        )
    }
}
