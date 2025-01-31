package tilbakekreving.domain.vurdert

import arrow.core.Nel
import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import java.util.UUID

data class OppdaterBrevtekstCommand(
    val sakId: UUID,
    val behandlingId: TilbakekrevingsbehandlingId,
    val brevvalg: Brevvalg.SaksbehandlersValg,
    val utførtAv: NavIdentBruker.Saksbehandler,
    val correlationId: CorrelationId,
    val brukerroller: Nel<Brukerrolle>,
    val klientensSisteSaksversjon: Hendelsesversjon,
)
