package no.nav.su.se.bakover.domain.beregning.beregning

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.Beregningsgrunnlag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import org.junit.jupiter.api.Test

internal class EpsUnder67BeregningTest {
    /**
     * Eksempel fra 01.05.2020
     +-------------+----------+------------+---------+----------------------------+------------+
     |   Bruker    |          |    EPS     |         |           Total            |            |
     +-------------+----------+------------+---------+----------------------------+------------+
     | Stønadssats | 251.350  | Folketrygd | 98.880  | SUM Bruker                 |    182.578 |
     | Folketrygd  | -68.772  |            |         | Fradrag EPS                |    -98.880 |
     | Sum         | =182.578 | Sum        | =98.880 | SUM SU/år                  |     83.698 |
     |             |          |            |         | SUM SU/mnd                 | 6.794,8333 |
     |             |          |            |         | Utbetalt SU/år (avrundet)  |     83.700 |
     |             |          |            |         | Utbetalt SU/mnd (avrundet) |      6.975 |
     +-------------+----------+------------+---------+----------------------------+------------+
     */
    @Test
    fun `beregningseksempel fra fagsiden`() {
        val periode = Periode(1.mai(2020), 30.april(2021))
        val folketrygd = 68772.0
        val folketrygdEps = 98880.0
        val beregningsgrunnlag = Beregningsgrunnlag(
            beregningsperiode = periode,
            fraSaksbehandler = listOf(
                FradragFactory.ny(
                    type = Fradragstype.ForventetInntekt,
                    beløp = 0.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                ),
                FradragFactory.ny(
                    type = Fradragstype.OffentligPensjon,
                    beløp = folketrygd,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                ),
                FradragFactory.ny(
                    type = Fradragstype.OffentligPensjon,
                    beløp = folketrygdEps,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS
                )
            )
        )

        BeregningStrategy.EpsUnder67År.beregn(beregningsgrunnlag).let {
            it.getSumYtelse() shouldBe 83700
            it.getSumFradrag() shouldBe (folketrygd + folketrygdEps).plusOrMinus(0.5)
            it.getMånedsberegninger().forEach {
                it.getSumYtelse() shouldBe 6975
            }
        }
    }
}
