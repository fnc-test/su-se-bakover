package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.fixedClock
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingKlargjortForOversendelse
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.sak.lagUtbetalingForStans
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.utbetaling.SimulerStansFeilet
import no.nav.su.se.bakover.service.utbetaling.UtbetalStansFeil
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.beregnetRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.oversendtStansUtbetalingUtenKvittering
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simuleringFeilutbetaling
import no.nav.su.se.bakover.test.simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.simulertUtbetaling
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.util.UUID

internal class StansAvYtelseServiceTest {

    @Test
    fun `svarer med feil dersom vi ikke får tak i gjeldende grunnlagdata`() {
        RevurderingServiceMocks(
            sakService = mock {
                on {
                    hentSak(
                        any<UUID>(),
                        any(),
                    )
                } doReturn søknadsbehandlingIverksattAvslagUtenBeregning().first.right()
            },
        ).let {
            it.revurderingService.stansAvYtelse(
                StansYtelseRequest.Opprett(
                    sakId = sakId,
                    saksbehandler = saksbehandler,
                    fraOgMed = 1.mai(2021),
                    revurderingsårsak = Revurderingsårsak.create(
                        årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                        begrunnelse = "begrunnelse",
                    ),
                ),
            ) shouldBe KunneIkkeStanseYtelse.KunneIkkeOppretteRevurdering.left()

            verify(it.sakService).hentSak(
                sakId = sakId,
                sessionContext = TestSessionFactory.transactionContext,
            )
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `får ikke opprettet dersom sak har åpen behandling`() {
        RevurderingServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>(), any()) } doReturn søknadsbehandlingVilkårsvurdertUavklart().first.right()
            },
        ).let {
            it.revurderingService.stansAvYtelse(
                StansYtelseRequest.Opprett(
                    sakId = sakId,
                    saksbehandler = saksbehandler,
                    fraOgMed = 1.mai(2021),
                    revurderingsårsak = Revurderingsårsak.create(
                        årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                        begrunnelse = "begrunnelse",
                    ),
                ),
            ) shouldBe KunneIkkeStanseYtelse.SakHarÅpenBehandling.left()
        }
    }

    @Test
    fun `svarer med feil dersom simulering feiler`() {
        val clock = 1.april(2021).fixedClock()
        val periode = Periode.create(
            fraOgMed = LocalDate.now(clock).plusMonths(1).startOfMonth(),
            tilOgMed = år(2021).tilOgMed,
        )
        val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(periode),
            clock = clock,
        )

        RevurderingServiceMocks(
            utbetalingService = mock {
                on {
                    simulerStans(
                        any(),
                    )
                } doReturn SimulerStansFeilet.KunneIkkeSimulere(SimuleringFeilet.TekniskFeil).left()
            },
            sakService = mock {
                on { hentSak(any<UUID>(), any()) } doReturn sak.right()
            },
            clock = clock,
        ).let {
            it.revurderingService.stansAvYtelse(
                StansYtelseRequest.Opprett(
                    sakId = sakId,
                    saksbehandler = saksbehandler,
                    fraOgMed = 1.mai(2021),
                    revurderingsårsak = Revurderingsårsak.create(
                        årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                        begrunnelse = "begrunnelse",
                    ),
                ),
            ) shouldBe KunneIkkeStanseYtelse.SimuleringAvStansFeilet(
                SimulerStansFeilet.KunneIkkeSimulere(
                    SimuleringFeilet.TekniskFeil,
                ),
            ).left()

            verify(it.sakService).hentSak(
                sakId = sakId,
                sessionContext = TestSessionFactory.transactionContext,
            )
            verify(it.utbetalingService).simulerStans(any())
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `happy path for opprettelse`() {
        val clock = 1.april(2021).fixedClock()
        val periode = Periode.create(
            fraOgMed = LocalDate.now(clock).plusMonths(1).startOfMonth(),
            tilOgMed = år(2021).tilOgMed,
        )
        val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(periode),
            clock = clock,
        )

        RevurderingServiceMocks(
            utbetalingService = mock {
                on { simulerStans(any()) } doReturn simulertUtbetaling().right()
            },
            sakService = mock {
                on { hentSak(any<UUID>(), any()) } doReturn sak.right()
            },
            revurderingRepo = mock {
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
            clock = clock,
        ).also { serviceAndMocks ->
            val response = serviceAndMocks.revurderingService.stansAvYtelse(
                StansYtelseRequest.Opprett(
                    sakId = sakId,
                    saksbehandler = saksbehandler,
                    fraOgMed = 1.mai(2021),
                    revurderingsårsak = Revurderingsårsak.create(
                        årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                        begrunnelse = "begrunnelse",
                    ),
                ),
            ).getOrFail()

            verify(serviceAndMocks.sakService).hentSak(
                sakId = sakId,
                sessionContext = TestSessionFactory.transactionContext,
            )
            verify(serviceAndMocks.utbetalingService).simulerStans(
                utbetaling = argThat {
                    it.erStans() shouldBe true
                    it.tidligsteDato() shouldBe periode.fraOgMed
                    it.senesteDato() shouldBe periode.tilOgMed
                    it.behandler shouldBe saksbehandler
                },
            )
            verify(serviceAndMocks.revurderingRepo).lagre(
                revurdering = argThat { it shouldBe response },
                transactionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.observer).handle(
                argThat { event ->
                    event shouldBe StatistikkEvent.Behandling.Stans.Opprettet(response)
                },
            )
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med feil dersom oversendelse av stans til oppdrag feiler`() {
        val periode = Periode.create(
            fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
            tilOgMed = år(2021).tilOgMed,
        )
        val (sak, simulertStans) = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            periode = periode,
        )

        RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any(), any()) } doReturn sak
            },
            utbetalingService = mock {
                on {
                    klargjørStans(
                        any(),
                        any(),
                        any(),
                    )
                } doReturn UtbetalStansFeil.KunneIkkeSimulere(SimulerStansFeilet.KunneIkkeSimulere(SimuleringFeilet.TekniskFeil))
                    .left()
            },
        ).let { serviceAndMocks ->
            serviceAndMocks.revurderingService.iverksettStansAvYtelse(
                revurderingId = revurderingId,
                attestant = NavIdentBruker.Attestant(simulertStans.saksbehandler.navIdent),
            ) shouldBe KunneIkkeIverksetteStansYtelse.KunneIkkeUtbetale(
                UtbetalStansFeil.KunneIkkeSimulere(
                    SimulerStansFeilet.KunneIkkeSimulere(SimuleringFeilet.TekniskFeil),
                ),
            ).left()

            verify(serviceAndMocks.sakService).hentSakForRevurdering(
                revurderingId = simulertStans.id,
                sessionContext = TestSessionFactory.transactionContext,
            )
            verify(serviceAndMocks.utbetalingService).klargjørStans(
                utbetaling = argThat {
                    it.erStans() shouldBe true
                    it.tidligsteDato() shouldBe simulertStans.periode.fraOgMed
                    it.senesteDato() shouldBe simulertStans.periode.tilOgMed
                    it.behandler.navIdent shouldBe simulertStans.saksbehandler.navIdent
                },
                saksbehandlersSimulering = argThat { simulertStans.simulering },
                transactionContext = argThat { TestSessionFactory.transactionContext },
            )
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `happy path for iverksettelse`() {
        val periode = Periode.create(
            fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
            tilOgMed = år(2021).tilOgMed,
        )
        val (sak, simulertStans) = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(periode = periode)
        val utbetaling = oversendtStansUtbetalingUtenKvittering(stansDato = periode.fraOgMed)

        val callback =
            mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalStansFeil.KunneIkkeUtbetale, Utbetalingsrequest>> {
                on { it.invoke(any()) } doReturn utbetaling.utbetalingsrequest.right()
            }

        val serviceAndMocks = RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any(), any()) } doReturn sak
            },
            revurderingRepo = mock {
                on { hent(any(), any()) } doReturn simulertStans
            },
            utbetalingService = mock {
                on { klargjørStans(any(), any(), any()) } doReturn UtbetalingKlargjortForOversendelse(
                    utbetaling = utbetaling,
                    callback = callback,
                ).right()
            },
            vedtakService = mock {
                doNothing().whenever(it).lagre(any(), anyOrNull())
            },
        )

        val response = serviceAndMocks.revurderingService.iverksettStansAvYtelse(
            revurderingId = revurderingId,
            attestant = NavIdentBruker.Attestant(simulertStans.saksbehandler.navIdent),
        ).getOrFail()

        verify(serviceAndMocks.sakService).hentSakForRevurdering(
            revurderingId = simulertStans.id,
            sessionContext = TestSessionFactory.transactionContext,
        )
        verify(serviceAndMocks.utbetalingService).klargjørStans(
            utbetaling = argThat {
                it.erStans() shouldBe true
                it.tidligsteDato() shouldBe simulertStans.periode.fraOgMed
                it.senesteDato() shouldBe simulertStans.periode.tilOgMed
                it.behandler.navIdent shouldBe simulertStans.saksbehandler.navIdent
            },
            saksbehandlersSimulering = argThat { simulertStans.simulering },
            transactionContext = argThat { TestSessionFactory.transactionContext },
        )
        verify(serviceAndMocks.revurderingRepo).lagre(
            revurdering = argThat { it shouldBe response },
            transactionContext = argThat { TestSessionFactory.transactionContext },
        )
        val expectedVedtak = VedtakSomKanRevurderes.from(
            revurdering = response,
            utbetalingId = utbetaling.id,
            clock = fixedClock,
        )
        verify(serviceAndMocks.vedtakService).lagre(
            vedtak = argThat { vedtak ->
                vedtak.shouldBeEqualToIgnoringFields(
                    expectedVedtak,
                    VedtakSomKanRevurderes::id,
                )
            },
            sessionContext = argThat { TestSessionFactory.transactionContext },
        )

        verify(callback).invoke(utbetaling.utbetalingsrequest)
        val eventCaptor = argumentCaptor<StatistikkEvent.Behandling.Stans.Iverksatt>()
        verify(serviceAndMocks.observer, times(2)).handle(eventCaptor.capture())
        val iverksatt = eventCaptor.allValues[0]
        iverksatt shouldBe StatistikkEvent.Behandling.Stans.Iverksatt(
            vedtak = VedtakSomKanRevurderes.from(
                revurdering = response,
                utbetalingId = utbetaling.id,
                clock = fixedClock,
            ).copy(id = iverksatt.vedtak.id),
        )
        eventCaptor.allValues[1].shouldBeTypeOf<StatistikkEvent.Stønadsvedtak>()
        serviceAndMocks.verifyNoMoreInteractions()
    }

    @Test
    @Disabled("https://trello.com/c/5iblmYP9/1090-endre-sperre-for-10-endring-til-%C3%A5-v%C3%A6re-en-advarsel")
    fun `svarer med feil ved forsøk på å oppdatere revurderinger som ikke er av korrekt type`() {
        val (sak, enRevurdering) = beregnetRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(
            stønadsperiode = Stønadsperiode.create(år(2021)),
        )

        RevurderingServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>(), any()) } doReturn sak.right()
            },
            utbetalingService = mock {
                on { simulerStans(any()) } doReturn simulertUtbetaling().right()
            },
            revurderingRepo = mock {
                on { hent(any(), any()) } doReturn enRevurdering
            },
        ).let {
            val response = it.revurderingService.stansAvYtelse(
                StansYtelseRequest.Oppdater(
                    sakId = sakId,
                    saksbehandler = NavIdentBruker.Saksbehandler("sverre"),
                    fraOgMed = 1.desember(2021),
                    revurderingsårsak = Revurderingsårsak.create(
                        årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                        begrunnelse = "oppdatert",
                    ),
                    revurderingId = enRevurdering.id,
                ),
            )

            response shouldBe KunneIkkeStanseYtelse.UgyldigTypeForOppdatering(BeregnetRevurdering.IngenEndring::class)
                .left()

            verify(it.utbetalingService).simulerStans(
                utbetaling = sak.lagUtbetalingForStans(
                    stansdato = 1.desember(2021),
                    behandler = NavIdentBruker.Saksbehandler("sverre"),
                    clock = fixedClock,
                ).getOrFail(),
            )
            verify(it.revurderingRepo).hent(
                id = enRevurdering.id,
                sessionContext = TestSessionFactory.transactionContext,
            )
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `happy path for oppdatering`() {
        val clock = 1.februar(2021).fixedClock()
        val periode = Periode.create(
            fraOgMed = LocalDate.now(clock).plusMonths(1).startOfMonth(),
            tilOgMed = år(2021).tilOgMed,
        )
        val (sak, eksisterende) = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            periode = periode,
            clock = clock,
        )

        RevurderingServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>(), any()) } doReturn sak.right()
            },
            utbetalingService = mock {
                on { simulerStans(any()) } doReturn simulertUtbetaling().right()
            },
            revurderingRepo = mock {
                on { hent(any(), any()) } doReturn eksisterende
            },
            clock = clock,
        ).let { serviceAndMocks ->
            val response = serviceAndMocks.revurderingService.stansAvYtelse(
                StansYtelseRequest.Oppdater(
                    sakId = sakId,
                    saksbehandler = NavIdentBruker.Saksbehandler("kjeks"),
                    fraOgMed = mars(2021).fraOgMed,
                    revurderingsårsak = Revurderingsårsak.create(
                        årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                        begrunnelse = "fjas",
                    ),
                    revurderingId = eksisterende.id,
                ),
            ).getOrFail()

            response.let { oppdatert ->
                oppdatert.periode shouldBe mars(2021).rangeTo(desember(2021))
                oppdatert.saksbehandler shouldBe NavIdentBruker.Saksbehandler("kjeks")
                oppdatert.revurderingsårsak shouldBe Revurderingsårsak.create(
                    årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                    begrunnelse = "fjas",
                )
            }

            verify(serviceAndMocks.utbetalingService).simulerStans(
                utbetaling = argThat {
                    it.erStans() shouldBe true
                    it.tidligsteDato() shouldBe periode.fraOgMed
                    it.senesteDato() shouldBe periode.tilOgMed
                    it.behandler shouldBe NavIdentBruker.Saksbehandler("kjeks")
                },
            )
            verify(serviceAndMocks.sakService).hentSak(
                sakId = sak.id,
                sessionContext = TestSessionFactory.transactionContext,
            )
            verify(serviceAndMocks.revurderingRepo).lagre(
                revurdering = argThat { it shouldBe response },
                transactionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `får ikke iverksatt dersom simulering indikerer feilutbetaling`() {
        val periode = Periode.create(
            fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
            tilOgMed = år(2021).tilOgMed,
        )
        val (sak, eksisterende) = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            periode = periode,
            simulering = simuleringFeilutbetaling(*periode.måneder().toTypedArray()),
        )

        RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any(), any()) } doReturn sak
            },
        ).let {
            val response = it.revurderingService.iverksettStansAvYtelse(
                revurderingId = eksisterende.id,
                attestant = attestant,
            )

            response shouldBe KunneIkkeIverksetteStansYtelse.SimuleringIndikererFeilutbetaling.left()

            verify(it.sakService).hentSakForRevurdering(
                revurderingId = eksisterende.id,
                sessionContext = TestSessionFactory.transactionContext,
            )
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `får ikke opprettet ny hvis det allerede eksisterer åpen revurdering for stans`() {
        val periode = Periode.create(
            fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
            tilOgMed = år(2021).tilOgMed,
        )
        val eksisterende = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            periode = periode,
        )

        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>(), any()) } doReturn eksisterende.first.right()
        }

        RevurderingServiceMocks(
            sakService = sakServiceMock,
        ).let {
            val response = it.revurderingService.stansAvYtelse(
                StansYtelseRequest.Opprett(
                    sakId = sakId,
                    saksbehandler = NavIdentBruker.Saksbehandler("sverre"),
                    fraOgMed = 1.desember(2021),
                    revurderingsårsak = Revurderingsårsak.create(
                        årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                        begrunnelse = "oppdatert",
                    ),
                ),
            )

            response shouldBe KunneIkkeStanseYtelse.SakHarÅpenBehandling.left()

            verify(sakServiceMock).hentSak(
                sakId = sakId,
                sessionContext = TestSessionFactory.transactionContext,
            )
            it.verifyNoMoreInteractions()
        }
    }
}
