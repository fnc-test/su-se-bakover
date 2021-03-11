package no.nav.su.se.bakover.domain.visitor

import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.RevurderingVisitor
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingVisitor

class FinnAttestantVisitor : SøknadsbehandlingVisitor, RevurderingVisitor {
    var attestant: NavIdentBruker.Attestant? = null

    override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Uavklart) {}
    override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Innvilget) {}
    override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Avslag) {}
    override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Innvilget) {}
    override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Avslag) {}
    override fun visit(søknadsbehandling: Søknadsbehandling.Simulert) {}
    override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Innvilget) {
        attestant = søknadsbehandling.attestering.attestant
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.MedBeregning) {
        attestant = søknadsbehandling.attestering.attestant
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.UtenBeregning) {
        attestant = søknadsbehandling.attestering.attestant
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.UtenBeregning) {}
    override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.MedBeregning) {}
    override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Innvilget) {}

    override fun visit(søknadsbehandling: Søknadsbehandling.Iverksatt.Avslag.UtenBeregning) {
        attestant = søknadsbehandling.attestering.attestant
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Iverksatt.Avslag.MedBeregning) {
        attestant = søknadsbehandling.attestering.attestant
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget) {
        attestant = søknadsbehandling.attestering.attestant
    }

    override fun visit(revurdering: OpprettetRevurdering) {}
    override fun visit(revurdering: BeregnetRevurdering) {}
    override fun visit(revurdering: SimulertRevurdering) {}
    override fun visit(revurdering: RevurderingTilAttestering) {}
    override fun visit(revurdering: IverksattRevurdering) {
        attestant = revurdering.attestant
    }
}