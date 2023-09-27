package no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.behandling

import no.nav.su.se.bakover.domain.Sak
import tilbakekreving.domain.KanVurdere
import tilbakekreving.domain.MånedsvurderingerTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.Tilbakekrevingsbehandling
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.VurdertTilbakekrevingsbehandling
import tilbakekreving.domain.vurdert.OppdaterMånedsvurderingerCommand
import java.time.Clock

fun Sak.vurderTilbakekrevingsbehandling(
    command: OppdaterMånedsvurderingerCommand,
    clock: Clock,
): Pair<MånedsvurderingerTilbakekrevingsbehandlingHendelse, VurdertTilbakekrevingsbehandling> {
    return (this.hentTilbakekrevingsbehandling(command.behandlingsId) as? KanVurdere)?.let { behandling ->
        behandling.leggTilVurdering(
            command = command,
            tidligereHendelsesId = behandling.hendelseId,
            nesteVersjon = this.versjon.inc(),
            clock = clock,
        )
    } ?: throw IllegalStateException("Tilbakekrevingsbehandling ${command.behandlingsId} enten fantes ikke eller var ikke i KanVurdere tilstanden. Sak id $id, saksnummer $saksnummer")
}

fun Sak.hentTilbakekrevingsbehandling(id: TilbakekrevingsbehandlingId): Tilbakekrevingsbehandling? =
    this.behandlinger.tilbakekrevinger.hent(id)