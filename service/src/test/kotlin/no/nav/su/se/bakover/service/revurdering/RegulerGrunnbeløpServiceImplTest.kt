package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakType
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.revurdering.KunneIkkeBeregneOgSimulereRevurdering.MåSendeGrunnbeløpReguleringSomÅrsakSammenMedForventetInntekt
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.createRevurderingService
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.periode
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.revurderingId
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.revurderingsårsak
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.revurderingsårsakRegulerGrunnbeløp
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.saksbehandler
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.søknadsbehandlingVedtak
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.junit.jupiter.api.Test

internal class RegulerGrunnbeløpServiceImplTest {

    @Test
    fun `kan ikke beregne og simulere reguler grunnbeløp med feil årsak`() {
        val opprettetRevurdering = OpprettetRevurdering(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            behandlingsinformasjon = søknadsbehandlingVedtak.behandlingsinformasjon,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn opprettetRevurdering
        }
        val simulertUtbetaling = mock<Utbetaling.SimulertUtbetaling> {
            on { simulering } doReturn mock()
        }
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerUtbetaling(any(), any(), any()) } doReturn simulertUtbetaling.right()
        }

        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock,
        ).beregnOgSimuler(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 10000.0,
                    periode = søknadsbehandlingVedtak.periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            forventetInntekt = 1,
        )

        actual shouldBe MåSendeGrunnbeløpReguleringSomÅrsakSammenMedForventetInntekt.left()

        inOrder(revurderingRepoMock, utbetalingServiceMock) {
            verify(revurderingRepoMock).hent(revurderingId)
        }
        verifyNoMoreInteractions(revurderingRepoMock, utbetalingServiceMock)
    }

    @Test
    fun `oppdaterer behandlingsinformasjon med forventet inntekt`() {
        val opprettetRevurdering = OpprettetRevurdering(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsakRegulerGrunnbeløp,
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn opprettetRevurdering
        }
        val simulertUtbetaling = mock<Utbetaling.SimulertUtbetaling> {
            on { simulering } doReturn mock()
        }
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerUtbetaling(any(), any(), any()) } doReturn simulertUtbetaling.right()
        }

        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock,
        ).beregnOgSimuler(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 10000.0,
                    periode = søknadsbehandlingVedtak.periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            forventetInntekt = 1,
        ).orNull()!! as SimulertRevurdering.Innvilget

        actual shouldBe SimulertRevurdering.Innvilget(
            tilRevurdering = søknadsbehandlingVedtak,
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            beregning = actual.beregning,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsakRegulerGrunnbeløp,
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt().copy(
                uførhet = opprettetRevurdering.behandlingsinformasjon.uførhet!!.copy(
                    forventetInntekt = 1,
                ),
            ),
            simulering = simulertUtbetaling.simulering,
        )

        inOrder(revurderingRepoMock, utbetalingServiceMock) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
            verify(utbetalingServiceMock).simulerUtbetaling(any(), any(), any())
            verify(revurderingRepoMock).lagre(argThat { it shouldBe actual })
        }
        verifyNoMoreInteractions(revurderingRepoMock, utbetalingServiceMock)
    }

    @Test
    fun `Ikke lov å sende en Simulert Opphørt til attestering`() {
        val simulertRevurdering = SimulertRevurdering.Opphørt(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsakRegulerGrunnbeløp,
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
            beregning = mock(),
            simulering = mock(),
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn simulertRevurdering
            on { hentEventuellTidligereAttestering(any()) } doReturn mock()
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn RevurderingTestUtils.aktørId.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveid").right()
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        shouldThrow<IllegalStateException> {
            createRevurderingService(
                revurderingRepo = revurderingRepoMock,
                personService = personServiceMock,
                oppgaveService = oppgaveServiceMock,
            ).sendTilAttestering(
                SendTilAttesteringRequest(
                    revurderingId = revurderingId,
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = "Fritekst",
                    skalFøreTilBrevutsending = true,
                ),
            )
        }.message shouldBe "Kan ikke sende en opphørt g-regulering til attestering"

        inOrder(revurderingRepoMock, personServiceMock, oppgaveServiceMock) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(personServiceMock).hentAktørId(argThat { it shouldBe RevurderingTestUtils.fnr })

            verify(revurderingRepoMock).hentEventuellTidligereAttestering(revurderingId)

            verify(oppgaveServiceMock).opprettOppgave(any())
            verify(oppgaveServiceMock).lukkOppgave(any())
        }
        verifyNoMoreInteractions(revurderingRepoMock, personServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `Ikke lov å sende en Underkjent Opphørt til attestering`() {
        val simulertRevurdering = UnderkjentRevurdering.Opphørt(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsakRegulerGrunnbeløp,
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
            beregning = mock(),
            simulering = mock(),
            attestering = mock(),
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn simulertRevurdering
            on { hentEventuellTidligereAttestering(any()) } doReturn mock()
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn RevurderingTestUtils.aktørId.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveid").right()
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        shouldThrow<IllegalStateException> {
            createRevurderingService(
                revurderingRepo = revurderingRepoMock,
                personService = personServiceMock,
                oppgaveService = oppgaveServiceMock,
            ).sendTilAttestering(
                SendTilAttesteringRequest(
                    revurderingId = revurderingId,
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = "Fritekst",
                    skalFøreTilBrevutsending = true,
                ),
            )
        }.message shouldBe "Kan ikke sende en opphørt g-regulering til attestering"

        inOrder(revurderingRepoMock, personServiceMock, oppgaveServiceMock) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(personServiceMock).hentAktørId(argThat { it shouldBe RevurderingTestUtils.fnr })

            verify(revurderingRepoMock).hentEventuellTidligereAttestering(revurderingId)

            verify(oppgaveServiceMock).opprettOppgave(any())
            verify(oppgaveServiceMock).lukkOppgave(any())
        }
        verifyNoMoreInteractions(revurderingRepoMock, personServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `En attestert beregnet revurdering skal ikke sende brev`() {
        val beregnetRevurdering = BeregnetRevurdering.IngenEndring(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsakRegulerGrunnbeløp,
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
            beregning = mock(),
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn beregnetRevurdering
            on { hentEventuellTidligereAttestering(any()) } doReturn mock()
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn RevurderingTestUtils.aktørId.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveid").right()
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
        ).sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "Fritekst",
                skalFøreTilBrevutsending = true,
            ),
        ).orNull()!! as RevurderingTilAttestering.IngenEndring

        actual shouldBe RevurderingTilAttestering.IngenEndring(
            tilRevurdering = søknadsbehandlingVedtak,
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "Fritekst",
            revurderingsårsak = revurderingsårsakRegulerGrunnbeløp,
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
            beregning = actual.beregning,
            skalFøreTilBrevutsending = false,
        )

        inOrder(revurderingRepoMock, personServiceMock, oppgaveServiceMock) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(personServiceMock).hentAktørId(argThat { it shouldBe RevurderingTestUtils.fnr })

            verify(revurderingRepoMock).hentEventuellTidligereAttestering(revurderingId)

            verify(oppgaveServiceMock).opprettOppgave(any())
            verify(oppgaveServiceMock).lukkOppgave(any())

            verify(revurderingRepoMock).lagre(argThat { it shouldBe actual })
        }
        verifyNoMoreInteractions(revurderingRepoMock, personServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `En attestert underkjent revurdering skal ikke sende brev`() {
        val underkjentRevurdering = UnderkjentRevurdering.IngenEndring(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsakRegulerGrunnbeløp,
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
            beregning = mock(),
            attestering = mock(),
            skalFøreTilBrevutsending = true,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn underkjentRevurdering
            on { hentEventuellTidligereAttestering(any()) } doReturn mock()
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn RevurderingTestUtils.aktørId.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveid").right()
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
        ).sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "Fritekst",
                skalFøreTilBrevutsending = true,
            ),
        ).orNull()!! as RevurderingTilAttestering.IngenEndring

        actual shouldBe RevurderingTilAttestering.IngenEndring(
            tilRevurdering = søknadsbehandlingVedtak,
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "Fritekst",
            revurderingsårsak = revurderingsårsakRegulerGrunnbeløp,
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
            beregning = actual.beregning,
            skalFøreTilBrevutsending = false,
        )

        inOrder(revurderingRepoMock, personServiceMock, oppgaveServiceMock) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(personServiceMock).hentAktørId(argThat { it shouldBe RevurderingTestUtils.fnr })

            verify(revurderingRepoMock).hentEventuellTidligereAttestering(revurderingId)

            verify(oppgaveServiceMock).opprettOppgave(any())
            verify(oppgaveServiceMock).lukkOppgave(any())

            verify(revurderingRepoMock).lagre(argThat { it shouldBe actual })
        }
        verifyNoMoreInteractions(revurderingRepoMock, personServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `iverksetter endring av ytelse`() {
        val attestant = NavIdentBruker.Attestant("attestant")

        val iverksattRevurdering = IverksattRevurdering.IngenEndring(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingVedtak,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId(value = "OppgaveId"),
            beregning = TestBeregning,
            attestering = Attestering.Iverksatt(attestant),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            behandlingsinformasjon = søknadsbehandlingVedtak.behandlingsinformasjon,
            skalFøreTilBrevutsending = false,
        )
        val revurderingTilAttestering = RevurderingTilAttestering.IngenEndring(
            id = revurderingId,
            periode = periode,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingVedtak,
            oppgaveId = OppgaveId(value = "OppgaveId"),
            beregning = TestBeregning,
            saksbehandler = saksbehandler,
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            behandlingsinformasjon = søknadsbehandlingVedtak.behandlingsinformasjon,
            skalFøreTilBrevutsending = false,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn revurderingTilAttestering
        }

        val vedtakRepoMock = mock<VedtakRepo>()
        val eventObserver: EventObserver = mock()

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            vedtakRepo = vedtakRepoMock,
        ).apply { addObserver(eventObserver) }
            .iverksett(
                revurderingId = revurderingTilAttestering.id,
                attestant = attestant,
            ) shouldBe iverksattRevurdering.right()

        inOrder(
            revurderingRepoMock,
            vedtakRepoMock,
            eventObserver,
        ) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
            verify(vedtakRepoMock).lagre(
                argThat {
                    it should beOfType<Vedtak.IngenEndringIYtelse>()
                    it.vedtakType shouldBe VedtakType.INGEN_ENDRING
                },
            )
            verify(revurderingRepoMock).lagre(argThat { it shouldBe iverksattRevurdering })
            verify(eventObserver).handle(
                argThat {
                    it shouldBe Event.Statistikk.RevurderingStatistikk.RevurderingIverksatt(iverksattRevurdering)
                },
            )
        }
        verifyNoMoreInteractions(
            revurderingRepoMock,
            vedtakRepoMock,
        )
    }
}
