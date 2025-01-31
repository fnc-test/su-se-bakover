package tilbakekreving.domain.kravgrunnlag

import arrow.core.nonEmptyListOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.test.kravgrunnlag.kravgrunnlag
import org.junit.jupiter.api.Test
import java.math.BigDecimal

internal class KravgrunnlagTest {
    @Test
    fun `ytelsesskatt kan være 0 med desimaler`() {
        kravgrunnlag(
            grunnlagsperioder = nonEmptyListOf(
                Kravgrunnlag.Grunnlagsmåned(
                    måned = januar(2021),
                    betaltSkattForYtelsesgruppen = BigDecimal("0.00"),
                    ytelse = Kravgrunnlag.Grunnlagsmåned.Ytelse(
                        beløpTidligereUtbetaling = 24515,
                        beløpNyUtbetaling = 3997,
                        beløpSkalTilbakekreves = 20518,
                        beløpSkalIkkeTilbakekreves = 0,
                        skatteProsent = BigDecimal("0.0000"),
                    ),
                    feilutbetaling = Kravgrunnlag.Grunnlagsmåned.Feilutbetaling(
                        beløpTidligereUtbetaling = 0,
                        beløpNyUtbetaling = 20518,
                        beløpSkalTilbakekreves = 0,
                        beløpSkalIkkeTilbakekreves = 0,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `ytelsesskatt kan ikke være negativ`() {
        shouldThrow<IllegalArgumentException> {
            kravgrunnlag(
                grunnlagsperioder = nonEmptyListOf(
                    Kravgrunnlag.Grunnlagsmåned(
                        måned = januar(2021),
                        betaltSkattForYtelsesgruppen = BigDecimal("0.00"),
                        ytelse = Kravgrunnlag.Grunnlagsmåned.Ytelse(
                            beløpTidligereUtbetaling = 24515,
                            beløpNyUtbetaling = 3997,
                            beløpSkalTilbakekreves = 20518,
                            beløpSkalIkkeTilbakekreves = 0,
                            skatteProsent = BigDecimal("-0.0001"),
                        ),
                        feilutbetaling = Kravgrunnlag.Grunnlagsmåned.Feilutbetaling(
                            beløpTidligereUtbetaling = 0,
                            beløpNyUtbetaling = 20518,
                            beløpSkalTilbakekreves = 0,
                            beløpSkalIkkeTilbakekreves = 0,
                        ),
                    ),
                ),
            )
        }.message shouldBe "Forventer at kravgrunnlag.skatteProsent >= 0, men var -0.0001"
    }
}
