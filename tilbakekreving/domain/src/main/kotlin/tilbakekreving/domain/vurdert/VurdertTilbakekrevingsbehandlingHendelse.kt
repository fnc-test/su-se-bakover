@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som TilbakekrevingsbehandlingHendelse (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import tilbakekreving.domain.vurdert.VurderCommand
import tilbakekreving.domain.vurdert.Vurderinger
import java.time.Clock
import java.util.UUID

data class MånedsvurderingerTilbakekrevingsbehandlingHendelse(
    override val hendelseId: HendelseId,
    override val sakId: UUID,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
    override val meta: DefaultHendelseMetadata,
    override val tidligereHendelseId: HendelseId,
    override val id: TilbakekrevingsbehandlingId,
    override val utførtAv: NavIdentBruker.Saksbehandler,
    val vurderinger: Vurderinger,
) : TilbakekrevingsbehandlingHendelse {
    override val entitetId: UUID = sakId
    override fun compareTo(other: Sakshendelse): Int {
        require(this.entitetId == other.entitetId && this.sakId == other.sakId)
        return this.versjon.compareTo(other.versjon)
    }

    override fun applyToState(behandling: Tilbakekrevingsbehandling): UnderBehandling {
        return when (behandling) {
            is KanVurdere -> behandling.leggTilVurderinger(
                månedsvurderinger = this.vurderinger,
                hendelseId = this.hendelseId,
                versjon = this.versjon,
            )
            is AvbruttTilbakekrevingsbehandling,
            is IverksattTilbakekrevingsbehandling,
            is TilbakekrevingsbehandlingTilAttestering,
            -> throw IllegalArgumentException("Kan ikke gå fra [Avbrutt, Iverksatt, TilAttestering] -> Vurdert. Hendelse ${this.hendelseId}, for sak ${this.sakId} ")
        }
    }
}

fun KanVurdere.leggTilVurdering(
    command: VurderCommand,
    tidligereHendelsesId: HendelseId,
    nesteVersjon: Hendelsesversjon,
    clock: Clock,
): Pair<MånedsvurderingerTilbakekrevingsbehandlingHendelse, UnderBehandling> {
    val hendelse = MånedsvurderingerTilbakekrevingsbehandlingHendelse(
        hendelseId = HendelseId.generer(),
        sakId = command.sakId,
        hendelsestidspunkt = Tidspunkt.now(clock),
        versjon = nesteVersjon,
        tidligereHendelseId = tidligereHendelsesId,
        meta = DefaultHendelseMetadata(
            correlationId = command.correlationId,
            ident = command.utførtAv,
            brukerroller = command.brukerroller,
        ),
        id = command.behandlingsId,
        utførtAv = command.utførtAv,
        vurderinger = Vurderinger(command.vurderinger.toNonEmptyList()),
    )
    return hendelse to hendelse.applyToState(this)
}
