package no.nav.su.se.bakover.client.dokarkiv

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class JournalpostTest {
    @Test
    fun `dokumentvariant inneholder korrekte verdier`() {
        DokumentVariant.ArkivPDF("doc").let {
            it.filtype shouldBe "PDFA"
            it.fysiskDokument shouldBe "doc"
            it.variantformat shouldBe "ARKIV"
        }

        DokumentVariant.OriginalJson("doc").let {
            it.filtype shouldBe "JSON"
            it.fysiskDokument shouldBe "doc"
            it.variantformat shouldBe "ORIGINAL"
        }
    }
}
