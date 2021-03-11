package no.nav.su.se.bakover.service.beregning

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningStrategyFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling

class BeregningService {
    fun beregn(søknadsbehandling: Søknadsbehandling, periode: Periode, fradrag: List<Fradrag>): Beregning {
        return BeregningStrategyFactory().beregn(søknadsbehandling, periode, fradrag)
    }
}