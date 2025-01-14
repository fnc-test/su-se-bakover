package no.nav.su.se.bakover.domain.revurdering.iverksett

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.juni
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.domain.oppdrag.KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.sak.SimulerUtbetalingFeilet
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.revurderingTilAttestering
import no.nav.su.se.bakover.test.satsFactoryTest
import no.nav.su.se.bakover.test.simulering.simuleringFeilutbetaling
import no.nav.su.se.bakover.test.simulering.simulertMånedFeilutbetalingVedOpphør
import org.junit.jupiter.api.Test

class IverksettRevurderingTest {

    @Test
    fun `kan ikke iverksette hvis attestants simulering er forskjellig`() {
        val clock = TikkendeKlokke()
        val mai = mai(2021)
        val revurderingsperiode = april(2021)..mai
        val (sak, revurdering) = revurderingTilAttestering(
            clock = clock,
            revurderingsperiode = revurderingsperiode,
            grunnlagsdataOverrides = listOf(
                // Tvinger fram et opphør.
                fradragsgrunnlagArbeidsinntekt(periode = revurderingsperiode, arbeidsinntekt = 100000.0),
            ),
            utbetalingerKjørtTilOgMed = { 1.juni(2021) },
        )
        val simulering = revurdering.simulering!!
        sak.iverksettRevurdering(
            revurderingId = revurdering.id,
            attestant = attestant,
            clock = clock,
            satsFactory = satsFactoryTest.gjeldende(clock),
            simuler = { utbetalingForSimulering: Utbetaling.UtbetalingForSimulering, _: Periode ->
                // Denne vil kjøres 2 ganger, en for april-mai og en for april-desember.
                val aprilFraFørsteSimulering = simulering.måneder.first()
                Utbetaling.SimulertUtbetaling(
                    simulering = simuleringFeilutbetaling(
                        gjelderId = sak.fnr,
                        simulertePerioder = listOf(
                            // April er uendet.
                            aprilFraFørsteSimulering,
                            simulertMånedFeilutbetalingVedOpphør(
                                måned = mai,
                                // Øker beløpet med 1000 siden saksbehandlers simulering for å emulere enn større feilutbetaling.
                                tidligereBeløp = 21946,
                            ),
                        ),
                    ),
                    utbetalingForSimulering = utbetalingForSimulering,
                ).right()
            },
            lagDokument = { throw IllegalStateException("Bør feile før dokument lages") },
        ) shouldBe KunneIkkeIverksetteRevurdering.Saksfeil.KontrollsimuleringFeilet(
            SimulerUtbetalingFeilet.FeilVedKryssjekkAvSaksbehandlerOgAttestantsSimulering(
                KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UlikFeilutbetaling,
            ),
        ).left()
    }
}
