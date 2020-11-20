package no.nav.su.se.bakover.domain.beregning.fradrag

import io.kotest.assertions.arrow.either.shouldBeRight
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Minstepensjonsnivå
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class FradragStrategyTest {
    @Test
    fun `hver delperiode må inneholde nøyaktig ett fradrag for brukers forventede inntekt`() {
        val periode = Periode(1.januar(2020), 31.desember(2020))
        assertThrows<IllegalArgumentException> {
            FradragStrategy.Enslig.beregn(
                fradrag = listOf(
                    lagFradrag(
                        Fradragstype.ForventetInntekt,
                        5000.0,
                        Periode(1.januar(2020), 31.januar(2020))
                    )
                ),
                beregningsperiode = periode
            )
        }.let {
            it.message shouldContain "Hele beregningsperioden må inneholde fradrag for brukers forventede inntekt etter uførhet."
        }
        assertThrows<IllegalArgumentException> {
            FradragStrategy.EpsOver67År.beregn(
                fradrag = listOf(
                    lagFradrag(
                        Fradragstype.ForventetInntekt,
                        5000.0,
                        Periode(1.januar(2020), 31.januar(2020))
                    )
                ),
                beregningsperiode = periode
            )
        }.let {
            it.message shouldContain "Hele beregningsperioden må inneholde fradrag for brukers forventede inntekt etter uførhet."
        }
        assertThrows<IllegalArgumentException> {
            FradragStrategy.EpsUnder67ÅrOgUførFlyktning.beregn(
                fradrag = listOf(
                    lagFradrag(
                        Fradragstype.ForventetInntekt,
                        5000.0,
                        Periode(1.januar(2020), 31.januar(2020))
                    )
                ),
                beregningsperiode = periode
            )
        }.let {
            it.message shouldContain "Hele beregningsperioden må inneholde fradrag for brukers forventede inntekt etter uførhet."
        }
        assertThrows<IllegalArgumentException> {
            FradragStrategy.EpsUnder67År.beregn(
                fradrag = listOf(
                    lagFradrag(
                        Fradragstype.ForventetInntekt,
                        5000.0,
                        Periode(1.januar(2020), 31.januar(2020))
                    )
                ),
                beregningsperiode = periode
            )
        }.let {
            it.message shouldContain "Hele beregningsperioden må inneholde fradrag for brukers forventede inntekt etter uførhet."
        }
    }

    @Nested
    inner class `fribeløp EPS` {
        val periode = Periode(1.januar(2020), 31.januar(2020))

        @Test
        fun `EPS over 67 år bruker minste pensjonsnivå`() {
            FradragStrategy.fromName(FradragStrategyName.EpsOver67År)
                .getEpsFribeløp(periode) shouldBe 15159.0.plusOrMinus(0.5)
        }

        @Test
        fun `EPS under 67 år ufør flyktning bruker ordinær SU-sats`() {
            FradragStrategy.fromName(FradragStrategyName.EpsUnder67ÅrOgUførFlyktning)
                .getEpsFribeløp(periode) shouldBe 18973.0.plusOrMinus(0.5)
        }

        @Test
        fun `Enslig gir ikke fribeløp EPS`() {
            FradragStrategy.fromName(FradragStrategyName.Enslig)
                .getEpsFribeløp(periode) shouldBe 0.0
        }

        @Test
        fun `EPS under 67 ikke ufør flyktning gir ikke fribeløp EPS`() {
            FradragStrategy.fromName(FradragStrategyName.EpsUnder67År)
                .getEpsFribeløp(periode) shouldBe 0.0
        }
    }
}

internal fun lagFradrag(
    type: Fradragstype,
    beløp: Double,
    periode: Periode,
    tilhører: FradragTilhører = FradragTilhører.BRUKER
) = FradragFactory.ny(
    type = type,
    beløp = beløp,
    periode = periode,
    utenlandskInntekt = null,
    tilhører = tilhører
)
