package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.util.UUID

/**
 * Motparten til [VedtakEndringIYtelse].
 * Vil ikke ha nye utbetalingslinjer (fører ikke til en utbetaling mot oppdrag)
 */
sealed interface VedtakIngenEndringIYtelse : VedtakSomKanRevurderes {
    abstract override val id: UUID
    abstract override val opprettet: Tidspunkt
    abstract override val behandling: Behandling
    abstract override val saksbehandler: NavIdentBruker.Saksbehandler
    abstract override val attestant: NavIdentBruker.Attestant
    abstract override val periode: Periode
    override val beregning: Beregning
    override val simulering: Simulering
}