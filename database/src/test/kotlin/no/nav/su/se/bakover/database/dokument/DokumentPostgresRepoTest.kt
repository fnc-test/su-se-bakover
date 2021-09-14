package no.nav.su.se.bakover.database.dokument

import arrow.core.getOrHandle
import arrow.core.right
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.journal.JournalpostId
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.util.UUID

internal class DokumentPostgresRepoTest {

    @Test
    fun `lagrer og henter dokumenter`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val dokumentRepo = testDataHelper.dokumentRepo

            val sak = testDataHelper.nySakMedNySøknad()
            val etVedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            val enRevurdering = testDataHelper.tilIverksattRevurdering()

            val original = Dokument.MedMetadata.Vedtak(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                tittel = "tittel",
                generertDokument = "".toByteArray(),
                generertDokumentJson = """{"some":"json"}""",
                metadata = Dokument.Metadata(
                    sakId = sak.id,
                    søknadId = sak.søknad.id,
                    vedtakId = etVedtak.id,
                    revurderingId = enRevurdering.id,
                    bestillBrev = false,
                ),
            )
            dokumentRepo.lagre(original, testDataHelper.sessionFactory.newTransactionContext())

            val hentet = dokumentRepo.hentDokument(original.id)!!

            hentet.shouldBeEqualToIgnoringFields(
                original,
                original::generertDokument,
            )

            hentet.generertDokument contentEquals original.generertDokument

            dokumentRepo.hentForSak(sak.id) shouldHaveSize 1
            dokumentRepo.hentForSøknad(sak.søknad.id) shouldHaveSize 1
            dokumentRepo.hentForVedtak(etVedtak.id) shouldHaveSize 1
            dokumentRepo.hentForRevurdering(enRevurdering.id) shouldHaveSize 1
        }
    }

    @Test
    fun `lagrer bestilling av brev for dokumenter og oppdaterer`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val dokumentRepo = testDataHelper.dokumentRepo
            val sak = testDataHelper.nySakMedNySøknad()
            val original = Dokument.MedMetadata.Informasjon(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                tittel = "tittel",
                generertDokument = "".toByteArray(),
                generertDokumentJson = """{"some":"json"}""",
                metadata = Dokument.Metadata(
                    sakId = sak.id,
                    søknadId = sak.søknad.id,
                    bestillBrev = true,
                ),
            )
            dokumentRepo.lagre(original, testDataHelper.sessionFactory.newTransactionContext())

            val dokumentdistribusjon = dokumentRepo.hentDokumenterForDistribusjon().first()

            dokumentdistribusjon.dokument.id shouldBe original.id
            dokumentdistribusjon.journalføringOgBrevdistribusjon shouldBe JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert

            dokumentRepo.oppdaterDokumentdistribusjon(
                dokumentdistribusjon.journalfør { JournalpostId("jp").right() }.getOrHandle {
                    fail { "Skulle fått journalført" }
                },
            )

            val journalført = dokumentRepo.hentDokumenterForDistribusjon().first()

            journalført.journalføringOgBrevdistribusjon shouldBe JournalføringOgBrevdistribusjon.Journalført(
                JournalpostId("jp"),
            )

            dokumentRepo.oppdaterDokumentdistribusjon(
                journalført.distribuerBrev { BrevbestillingId("brev").right() }.getOrHandle {
                    fail { "Skulle fått bestilt brev" }
                },
            )

            dokumentRepo.hentDokumenterForDistribusjon() shouldBe emptyList()

            val journalførtOgBestiltBrev = dokumentRepo.hentDokumentdistribusjon(dokumentdistribusjon.id)!!

            journalførtOgBestiltBrev.journalføringOgBrevdistribusjon shouldBe JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                JournalpostId("jp"),
                BrevbestillingId("brev"),
            )
        }
    }
}
