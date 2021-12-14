package no.nav.su.se.bakover.client.kabal

import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import java.time.ZoneOffset

internal object KabalRequestMapper {
    fun map(
        klage: OversendtKlage,
        journalpostIdForVedtak: JournalpostId,
    ): KabalRequest {
        return KabalRequest(
            avsenderSaksbehandlerIdent = klage.saksbehandler.navIdent,
            dvhReferanse = klage.id.toString(),
            fagsak = KabalRequest.Fagsak(klage.saksnummer.toString()),
            /*
            hjemler = (klage.vurderinger.vedtaksvurdering as VurderingerTilKlage.Vedtaksvurdering.Utfylt.Oppretthold).hjemler.map {
                KabalRequest.Hjemler(
                    kapittel = it.kapittel,
                    lov = KabalRequest.Hjemler.Lov.SUPPLERENDE_STONAD,
                    paragraf = it.paragrafnummer,
                )
            },*/
            hjemler = listOf(KabalRequest.Hjemler(kapittel = 9, lov = KabalRequest.Hjemler.Lov.FOLKETRYGDLOVEN, paragraf = 1)),
            innsendtTilNav = klage.datoKlageMottatt,
            mottattFoersteinstans = klage.opprettet.toLocalDate(ZoneOffset.UTC),
            kildeReferanse = klage.id.toString(),
            klager = KabalRequest.Klager(
                id = KabalRequest.PartId(
                    verdi = klage.fnr.toString(),
                ),
            ),
            /* TODO ai: Se på å sende med journalpostId:n for OVERSENDELSESBREV:et, via en jobb */
            tilknyttedeJournalposter = listOf(
                KabalRequest.TilknyttedeJournalposter(
                    journalpostId = klage.journalpostId,
                    type = KabalRequest.TilknyttedeJournalposter.Type.BRUKERS_KLAGE,
                ),
                KabalRequest.TilknyttedeJournalposter(
                    journalpostId = journalpostIdForVedtak,
                    type = KabalRequest.TilknyttedeJournalposter.Type.OPPRINNELIG_VEDTAK
                )
            ),
        )
    }
}
