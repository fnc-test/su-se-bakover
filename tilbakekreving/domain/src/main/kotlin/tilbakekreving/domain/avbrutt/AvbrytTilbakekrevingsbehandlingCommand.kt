package tilbakekreving.domain.avbrutt

import arrow.core.Nel
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import java.util.UUID

data class AvbrytTilbakekrevingsbehandlingCommand(
    val sakId: UUID,
    val tilbakekrevingsbehandlingId: TilbakekrevingsbehandlingId,
    val utførtAv: NavIdentBruker.Saksbehandler,
    val correlationId: CorrelationId,
    val brukerroller: Nel<Brukerrolle>,
    val klientensSisteSaksversjon: Hendelsesversjon,
    val begrunnelse: String,
) {
    fun defaultHendelseMetadata() = DefaultHendelseMetadata(
        correlationId = correlationId,
        ident = utførtAv,
        brukerroller = brukerroller,
    )
}
