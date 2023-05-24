package no.nav.su.se.bakover.test.skatt

import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.skatt.Skattedokument
import java.util.UUID

fun nySkattedokumentGenerert(
    id: UUID = UUID.randomUUID(),
    søkersSkatteId: UUID = UUID.randomUUID(),
    epsSkatteId: UUID? = UUID.randomUUID(),
    sakId: UUID = UUID.randomUUID(),
    vedtakId: UUID = UUID.randomUUID(),
    generertDokument: PdfA = PdfA("jeg er en pdf".toByteArray()),
    dokumentJson: String = """{"key": "value"}""",
): Skattedokument.Generert = Skattedokument.Generert(
    id = id,
    søkersSkatteId = søkersSkatteId,
    epsSkatteId = epsSkatteId,
    sakid = sakId,
    vedtakid = vedtakId,
    generertDokument = generertDokument,
    dokumentJson = dokumentJson,
)

fun nySkattedokumentJournalført(
    id: UUID = UUID.randomUUID(),
    søkersSkatteId: UUID = UUID.randomUUID(),
    epsSkatteId: UUID = UUID.randomUUID(),
    sakId: UUID = UUID.randomUUID(),
    vedtakId: UUID = UUID.randomUUID(),
    generertDokument: PdfA = PdfA("jeg er en pdf".toByteArray()),
    dokumentJson: String = """{"key": "value"}""",
    journalpostId: JournalpostId = JournalpostId("123"),
): Skattedokument.Journalført = Skattedokument.Journalført(
    generert = nySkattedokumentGenerert(
        id = id,
        søkersSkatteId = søkersSkatteId,
        epsSkatteId = epsSkatteId,
        sakId = sakId,
        vedtakId = vedtakId,
        generertDokument = generertDokument,
        dokumentJson = dokumentJson,
    ),
    journalpostid = journalpostId,
)
