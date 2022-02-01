package no.nav.su.se.bakover.service.revurdering

import arrow.core.right
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalRequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.grunnlagsdataEnsligMedFradrag
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.oversendtUtbetalingUtenKvittering
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.revurderingTilAttestering
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.simulertUtbetaling
import no.nav.su.se.bakover.test.tilAttesteringRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class IverksettRevurderingTest {

    @Test
    fun `iverksett - iverksetter endring av ytelse`() {
        val sakOgVedtak = vedtakSøknadsbehandlingIverksattInnvilget()
        val grunnlagsdata = grunnlagsdataEnsligMedFradrag().let { it.fradragsgrunnlag + it.bosituasjon }

        val revurderingTilAttestering = revurderingTilAttestering(
            sakOgVedtakSomKanRevurderes = sakOgVedtak,
            grunnlagsdataOverrides = grunnlagsdata
        ).second as RevurderingTilAttestering.Innvilget

        val expected = iverksattRevurdering(
            sakOgVedtakSomKanRevurderes = sakOgVedtak,
            grunnlagsdataOverrides = grunnlagsdata
        ).second as IverksattRevurdering.Innvilget

        val simulertUtbetaling = simulertUtbetaling()

        val serviceAndMocks = RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurderingTilAttestering
                doNothing().whenever(it).lagre(any(), anyOrNull())
            },
            utbetalingService = mock {
                on { genererUtbetalingsRequest(any()) } doReturn simulertUtbetaling.right()
                on { publiserUtbetaling(any()) } doReturn Utbetalingsrequest("<xml></xml>").right()
            },
            vedtakRepo = mock {
                doNothing().whenever(it).lagre(any(), anyOrNull())
            }
        )

        val respone = serviceAndMocks.revurderingService.iverksett(
            revurderingId = revurderingTilAttestering.id,
            attestant = attestant,
        )

        respone shouldBe expected.right()

        verify(serviceAndMocks.revurderingRepo).hent(argThat { it shouldBe revurderingId })
        verify(serviceAndMocks.utbetalingService).genererUtbetalingsRequest(
            argThat {
                it shouldBe UtbetalRequest.NyUtbetaling(
                    request = SimulerUtbetalingRequest.NyUtbetaling(
                        sakId = sakId,
                        saksbehandler = attestant,
                        beregning = revurderingTilAttestering.beregning,
                        uføregrunnlag = revurderingTilAttestering.vilkårsvurderinger.uføre.grunnlag,
                    ),
                    simulering = revurderingTilAttestering.simulering,
                )
            }
        )
        verify(serviceAndMocks.utbetalingService).lagreUtbetaling(argThat { it shouldBe simulertUtbetaling }, anyOrNull())
        verify(serviceAndMocks.utbetalingService).publiserUtbetaling(argThat { it shouldBe simulertUtbetaling })
        verify(serviceAndMocks.vedtakRepo).lagre(
            argThat { it should beOfType<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering>() },
            anyOrNull()
        )
        verify(serviceAndMocks.revurderingRepo).lagre(argThat { it shouldBe expected }, anyOrNull())

        serviceAndMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `iverksett - iverksetter opphør av ytelse`() {
        val revurderingTilAttestering = tilAttesteringRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak().second
        val simulertUtbetaling = simulertUtbetaling()
        val serviceAndMocks = RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurderingTilAttestering
                doNothing().whenever(it).lagre(any(), anyOrNull())
            },
            utbetalingService = mock {
                on { genererOpphørsRequest(any()) } doReturn simulertUtbetaling.right()
                on { publiserUtbetaling(any()) } doReturn Utbetalingsrequest("<xml></xml>").right()
                on { lagreUtbetaling(any(), anyOrNull()) } doReturn oversendtUtbetalingUtenKvittering()
            },
            vedtakRepo = mock {
                doNothing().whenever(it).lagre(any(), anyOrNull())
            },
            kontrollsamtaleService = mock {
                on { annullerKontrollsamtale(any(), anyOrNull()) } doReturn Unit.right()
            }
        )

        serviceAndMocks.revurderingService.iverksett(revurderingId, attestant)

        verify(serviceAndMocks.revurderingRepo).hent(revurderingId)
        verify(serviceAndMocks.utbetalingService).genererOpphørsRequest(
            argThat {
                it shouldBe UtbetalRequest.Opphør(
                    request = SimulerUtbetalingRequest.Opphør(
                        sakId = sakId,
                        saksbehandler = attestant,
                        opphørsdato = revurderingTilAttestering.periode.fraOgMed,
                    ),
                    simulering = revurderingTilAttestering.simulering,
                )
            },
        )
        verify(serviceAndMocks.vedtakRepo).lagre(
            argThat { it should beOfType<VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering>() },
            anyOrNull(),
        )
        verify(serviceAndMocks.kontrollsamtaleService).annullerKontrollsamtale(argThat { it shouldBe revurderingTilAttestering.sakId }, anyOrNull())
        verify(serviceAndMocks.revurderingRepo).lagre(any(), anyOrNull())
        verify(serviceAndMocks.utbetalingService).lagreUtbetaling(argThat { it shouldBe simulertUtbetaling }, anyOrNull())
        verify(serviceAndMocks.utbetalingService).publiserUtbetaling(simulertUtbetaling)
        serviceAndMocks.verifyNoMoreInteractions()
    }
}
