package no.nav.su.se.bakover.domain.oppdrag.tilbakekreving

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Beløp
import no.nav.su.se.bakover.common.MånedBeløp
import no.nav.su.se.bakover.common.Månedsbeløp
import no.nav.su.se.bakover.common.extensions.juli
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.genererKravgrunnlagFraSimulering
import no.nav.su.se.bakover.test.iverksattRevurdering
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

internal class TilbakekrevingsbehandlingTest {
    @Test
    fun `tilbakekrevingsvedtak for full tilbakekreving`() {
        val (sak, revurdering, utbetaling) = iverksattRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = mai(2021),
                    arbeidsinntekt = 12500.0,
                ),
            ),
            utbetalingerKjørtTilOgMed = { 1.juli(2021) },
        )
        val kravgrunnlag = genererKravgrunnlagFraSimulering(
            saksnummer = revurdering.saksnummer,
            simulering = (revurdering as IverksattRevurdering.Innvilget).simulering,
            utbetalingId = utbetaling.id,
            clock = fixedClock,
        )

        MottattKravgrunnlag(
            avgjort = Tilbakekrev(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                sakId = sak.id,
                revurderingId = revurdering.id,
                periode = revurdering.periode,
            ),
            kravgrunnlag = kravgrunnlag,
            kravgrunnlagMottatt = fixedTidspunkt,
        ).lagTilbakekrevingsvedtak(kravgrunnlag) shouldBe Tilbakekrevingsvedtak.FullTilbakekreving(
            vedtakId = "654321",
            ansvarligEnhet = "8020",
            kontrollFelt = kravgrunnlag.eksternKontrollfelt,
            behandler = "K231B433",
            tilbakekrevingsperioder = listOf(
                Tilbakekrevingsvedtak.Tilbakekrevingsperiode(
                    periode = mai(2021),
                    renterBeregnes = false,
                    beløpRenter = BigDecimal.ZERO,
                    feilutbetaling = Tilbakekrevingsvedtak.Tilbakekrevingsperiode.Tilbakekrevingsbeløp.TilbakekrevingsbeløpFeilutbetaling(
                        beløpTidligereUtbetaling = BigDecimal.ZERO,
                        beløpNyUtbetaling = BigDecimal("12500"),
                        beløpSomSkalTilbakekreves = BigDecimal.ZERO,
                        beløpSomIkkeTilbakekreves = BigDecimal.ZERO,
                    ),
                    ytelse = Tilbakekrevingsvedtak.Tilbakekrevingsperiode.Tilbakekrevingsbeløp.TilbakekrevingsbeløpYtelse(
                        beløpTidligereUtbetaling = BigDecimal("20946"),
                        beløpNyUtbetaling = BigDecimal("8446"),
                        beløpSomSkalTilbakekreves = BigDecimal("12500"),
                        beløpSomIkkeTilbakekreves = BigDecimal.ZERO,
                        beløpSkatt = BigDecimal("6250"),
                        tilbakekrevingsresultat = Tilbakekrevingsvedtak.Tilbakekrevingsresultat.FULL_TILBAKEKREVING,
                        skyld = Tilbakekrevingsvedtak.Skyld.BRUKER,
                    ),
                ),
            ),
        ).also {
            it.brutto() shouldBe Månedsbeløp(
                listOf(
                    MånedBeløp(mai(2021), Beløp(12500)),
                ),
            )
            it.netto() shouldBe Månedsbeløp(
                listOf(
                    MånedBeløp(mai(2021), Beløp(6250)),
                ),
            )
            it.skatt() shouldBe Månedsbeløp(
                listOf(
                    MånedBeløp(mai(2021), Beløp(6250)),
                ),
            )
        }
    }

    @Test
    fun `justerer størrelsen på beløp for skatt i henhold til skatteprosent - beløp lavere enn total betalt skatt`() {
        val (sak, revurdering, utbetaling) = iverksattRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = mai(2021),
                    arbeidsinntekt = 4000.0,
                ),
            ),
        )
        val kravgrunnlag = genererKravgrunnlagFraSimulering(
            saksnummer = revurdering.saksnummer,
            simulering = (revurdering as IverksattRevurdering.Innvilget).simulering,
            utbetalingId = utbetaling.id,
            clock = fixedClock,
        )

        MottattKravgrunnlag(
            avgjort = Tilbakekrev(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                sakId = sak.id,
                revurderingId = revurdering.id,
                periode = revurdering.periode,
            ),
            kravgrunnlag = kravgrunnlag,
            kravgrunnlagMottatt = fixedTidspunkt,
        ).lagTilbakekrevingsvedtak(kravgrunnlag).let { tilbakekrevingsvedtak ->
            tilbakekrevingsvedtak.tilbakekrevingsperioder
                .filterIsInstance<Tilbakekrevingsvedtak.Tilbakekrevingsperiode.Tilbakekrevingsbeløp.TilbakekrevingsbeløpYtelse>()
                .all { it.beløpSkatt == BigDecimal("1759") && it.beløpSkatt < kravgrunnlag.grunnlagsmåneder[0].betaltSkattForYtelsesgruppen } shouldBe true
        }
    }

    @Test
    fun `justerer størrelsen på beløp for skatt i henhold til skatteprosent - beløp høyere enn total betalt skatt`() {
        val (sak, revurdering, utbetaling) = iverksattRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = mai(2021),
                    arbeidsinntekt = 20000.0,
                ),
            ),
        )
        val kravgrunnlag = genererKravgrunnlagFraSimulering(
            saksnummer = revurdering.saksnummer,
            simulering = (revurdering as IverksattRevurdering.Innvilget).simulering,
            utbetalingId = utbetaling.id,
            clock = fixedClock,
        )

        MottattKravgrunnlag(
            avgjort = Tilbakekrev(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                sakId = sak.id,
                revurderingId = revurdering.id,
                periode = revurdering.periode,
            ),
            kravgrunnlag = kravgrunnlag,
            kravgrunnlagMottatt = fixedTidspunkt,
        ).lagTilbakekrevingsvedtak(kravgrunnlag).let { tilbakekrevingsvedtak ->
            tilbakekrevingsvedtak.tilbakekrevingsperioder
                .filterIsInstance<Tilbakekrevingsvedtak.Tilbakekrevingsperiode.Tilbakekrevingsbeløp.TilbakekrevingsbeløpYtelse>()
                .all { it.beløpSkatt == BigDecimal("4395") } shouldBe true
        }
    }

    @Test
    fun `tilbakekrevingsvedtak for ingen tilbakekreving`() {
        val (sak, revurdering, utbetaling) = iverksattRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = mai(2021),
                    arbeidsinntekt = 12500.0,
                ),
            ),
            utbetalingerKjørtTilOgMed = { 1.juli(2021) },
        )
        val kravgrunnlag = genererKravgrunnlagFraSimulering(
            saksnummer = revurdering.saksnummer,
            simulering = (revurdering as IverksattRevurdering.Innvilget).simulering,
            utbetalingId = utbetaling.id,
            clock = fixedClock,
        )

        MottattKravgrunnlag(
            avgjort = IkkeTilbakekrev(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                sakId = sak.id,
                revurderingId = revurdering.id,
                periode = revurdering.periode,
            ),
            kravgrunnlag = kravgrunnlag,
            kravgrunnlagMottatt = fixedTidspunkt,
        ).lagTilbakekrevingsvedtak(kravgrunnlag) shouldBe Tilbakekrevingsvedtak.IngenTilbakekreving(
            vedtakId = "654321",
            ansvarligEnhet = "8020",
            kontrollFelt = kravgrunnlag.eksternKontrollfelt,
            behandler = "K231B433",
            tilbakekrevingsperioder = listOf(
                Tilbakekrevingsvedtak.Tilbakekrevingsperiode(
                    periode = mai(2021),
                    renterBeregnes = false,
                    beløpRenter = BigDecimal.ZERO,
                    feilutbetaling = Tilbakekrevingsvedtak.Tilbakekrevingsperiode.Tilbakekrevingsbeløp.TilbakekrevingsbeløpFeilutbetaling(
                        beløpTidligereUtbetaling = BigDecimal.ZERO,
                        beløpNyUtbetaling = BigDecimal("12500"),
                        beløpSomSkalTilbakekreves = BigDecimal.ZERO,
                        beløpSomIkkeTilbakekreves = BigDecimal.ZERO,
                    ),
                    ytelse = Tilbakekrevingsvedtak.Tilbakekrevingsperiode.Tilbakekrevingsbeløp.TilbakekrevingsbeløpYtelse(
                        beløpTidligereUtbetaling = BigDecimal("20946"),
                        beløpNyUtbetaling = BigDecimal("8446"),
                        beløpSomSkalTilbakekreves = BigDecimal.ZERO,
                        beløpSomIkkeTilbakekreves = BigDecimal("12500"),
                        beløpSkatt = BigDecimal.ZERO,
                        tilbakekrevingsresultat = Tilbakekrevingsvedtak.Tilbakekrevingsresultat.INGEN_TILBAKEKREVING,
                        skyld = Tilbakekrevingsvedtak.Skyld.IKKE_FORDELT,
                    ),
                ),
            ),
        ).also {
            it.brutto() shouldBe Månedsbeløp(
                listOf(
                    MånedBeløp(mai(2021), Beløp(0)),
                ),
            )
            it.netto() shouldBe Månedsbeløp(
                listOf(
                    MånedBeløp(mai(2021), Beløp(0)),
                ),
            )
            it.skatt() shouldBe Månedsbeløp(
                listOf(
                    MånedBeløp(mai(2021), Beløp(0)),
                ),
            )
        }
    }
}
