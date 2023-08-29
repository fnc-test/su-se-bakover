package no.nav.su.se.bakover.client.dokarkiv

import no.nav.su.se.bakover.client.dokarkiv.JournalpostDokument.Companion.lagDokumenterForJournalpostForSak
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.extensions.zoneIdOslo
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.skatt.Skattedokument
import java.time.LocalDate

/**
 * kan brukes som mal for 'Notat' poster i Joark.
 */
data class JournalpostSkatt(
    override val person: Person,
    override val saksnummer: Saksnummer,
    override val sakstype: Sakstype,
    override val tittel: String,
    override val dokumenter: List<JournalpostDokument>,
    override val datoDokument: LocalDate,
) : JournalpostForSak {
    override val sak: Fagsak = Fagsak(saksnummer.nummer.toString())
    override val journalpostType: JournalPostType = JournalPostType.NOTAT
    override val journalfoerendeEnhet: JournalførendeEnhet = JournalførendeEnhet.AUTOMATISK
    override val bruker: Bruker = Bruker(id = person.ident.fnr.toString())
    override val avsenderMottaker: AvsenderMottaker? = null
    override val kanal: String? = null

    companion object {
        fun Skattedokument.lagJournalpost(person: Person, sakInfo: SakInfo): JournalpostSkatt = JournalpostSkatt(
            person = person,
            saksnummer = sakInfo.saksnummer,
            sakstype = sakInfo.type,
            tittel = this.dokumentTittel,
            dokumenter = lagDokumenterForJournalpostForSak(
                tittel = this.dokumentTittel,
                pdf = this.generertDokument,
                originalJson = this.dokumentJson,
            ),
            datoDokument = this.skattedataHentet.toLocalDate(zoneIdOslo),
        )
    }
}
