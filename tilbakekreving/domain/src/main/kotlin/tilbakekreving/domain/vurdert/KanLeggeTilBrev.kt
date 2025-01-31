@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Tilbakekrevingsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.vurdert.Vurderinger

sealed interface KanLeggeTilBrev : KanEndres {
    override val månedsvurderinger: Vurderinger?
    override val vedtaksbrevvalg: Brevvalg.SaksbehandlersValg?

    fun oppdaterVedtaksbrev(
        vedtaksbrevvalg: Brevvalg.SaksbehandlersValg,
        hendelseId: HendelseId,
        versjon: Hendelsesversjon,
    ): UnderBehandling.Utfylt

    override fun erÅpen() = true
}
