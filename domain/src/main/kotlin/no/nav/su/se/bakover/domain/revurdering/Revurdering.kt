package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Beregningsgrunnlag
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import java.util.UUID

sealed class Revurdering {
    abstract val id: UUID
    abstract val opprettet: Tidspunkt
    abstract val tilRevurdering: Søknadsbehandling.Iverksatt.Innvilget
    abstract val periode: Periode
    abstract val saksbehandler: Saksbehandler
    open fun beregn(fradrag: List<Fradrag>): BeregnetRevurdering {
        val beregningsgrunnlag = Beregningsgrunnlag.create(
            beregningsperiode = periode,
            forventetInntektPerÅr = tilRevurdering.behandlingsinformasjon.uførhet?.forventetInntekt?.toDouble()
                ?: 0.0,
            fradragFraSaksbehandler = fradrag
        )

        return BeregnetRevurdering(
            tilRevurdering = tilRevurdering,
            id = id,
            periode = periode,
            opprettet = Tidspunkt.now(),
            beregning = tilRevurdering.behandlingsinformasjon.bosituasjon!!.getBeregningStrategy()
                .beregn(beregningsgrunnlag),
            saksbehandler = saksbehandler
        )
    }

    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson
}

data class OpprettetRevurdering(
    override val id: UUID = UUID.randomUUID(),
    override val periode: Periode,
    override val opprettet: Tidspunkt = Tidspunkt.now(),
    override val tilRevurdering: Søknadsbehandling.Iverksatt.Innvilget,
    override val saksbehandler: Saksbehandler,
) : Revurdering()

data class BeregnetRevurdering(
    override val id: UUID,
    override val periode: Periode,
    override val opprettet: Tidspunkt,
    override val tilRevurdering: Søknadsbehandling.Iverksatt.Innvilget,
    override val saksbehandler: Saksbehandler,
    val beregning: Beregning
) : Revurdering() {
    fun toSimulert(simulering: Simulering) = SimulertRevurdering(
        id = id,
        periode = periode,
        opprettet = opprettet,
        tilRevurdering = tilRevurdering,
        beregning = beregning,
        simulering = simulering,
        saksbehandler = saksbehandler
    )
}

data class SimulertRevurdering(
    override val id: UUID,
    override val periode: Periode,
    override val opprettet: Tidspunkt,
    override val tilRevurdering: Søknadsbehandling.Iverksatt.Innvilget,
    override val saksbehandler: Saksbehandler,
    val beregning: Beregning,
    val simulering: Simulering
) : Revurdering() {
    fun tilAttestering(oppgaveId: OppgaveId, saksbehandler: Saksbehandler): RevurderingTilAttestering = RevurderingTilAttestering(
        id = id,
        periode = periode,
        opprettet = opprettet,
        tilRevurdering = tilRevurdering,
        saksbehandler = saksbehandler,
        beregning = beregning,
        simulering = simulering,
        oppgaveId = oppgaveId,
    )
}

data class RevurderingTilAttestering(
    override val id: UUID,
    override val periode: Periode,
    override val opprettet: Tidspunkt,
    override val tilRevurdering: Søknadsbehandling.Iverksatt.Innvilget,
    override val saksbehandler: Saksbehandler,
    val beregning: Beregning,
    val simulering: Simulering,
    val oppgaveId: OppgaveId
) : Revurdering() {
    val sakId
        get() = this.tilRevurdering.sakId

    override fun beregn(fradrag: List<Fradrag>): BeregnetRevurdering {
        throw RuntimeException("Skal ikke kunne beregne når revurderingen er til attestering")
    }
    fun iverksett(attestant: NavIdentBruker.Attestant, utbetalingId: UUID30): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, IverksattRevurdering> {
        if (saksbehandler.navIdent == attestant.navIdent) {
            return AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
        }
        return IverksattRevurdering(
            id = id,
            periode = periode,
            opprettet = opprettet,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            beregning = beregning,
            simulering = simulering,
            oppgaveId = oppgaveId,
            attestant = attestant,
            utbetalingId = utbetalingId
        ).right()
    }
    fun underkjenn() { TODO() }
}

data class IverksattRevurdering(
    override val id: UUID,
    override val periode: Periode,
    override val opprettet: Tidspunkt,
    override val tilRevurdering: Søknadsbehandling.Iverksatt.Innvilget,
    override val saksbehandler: Saksbehandler,
    val beregning: Beregning,
    val simulering: Simulering,
    val oppgaveId: OppgaveId,
    val attestant: NavIdentBruker.Attestant,
    val utbetalingId: UUID30
) : Revurdering() {
    override fun beregn(fradrag: List<Fradrag>): BeregnetRevurdering {
        throw RuntimeException("Skal ikke kunne beregne når revurderingen er til attestering")
    }
}
