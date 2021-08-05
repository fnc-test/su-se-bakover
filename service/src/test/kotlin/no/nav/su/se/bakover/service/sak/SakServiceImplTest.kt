package no.nav.su.se.bakover.service.sak

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.sak.SakRestans
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.test.UnderkjentInnvilgetRevurderingFraInnvilgetSøknadsbehandlignsVedtak
import no.nav.su.se.bakover.test.journalførtSøknadMedOppgave
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.test.tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class SakServiceImplTest {

    @Test
    fun `Oppretter sak og publiserer event`() {
        val sakId = UUID.randomUUID()
        val sak: Sak = mock {
            on { id } doReturn sakId
        }
        val sakRepo: SakRepo = mock {
            on { hentSak(any<UUID>()) } doReturn sak
        }

        val observer: EventObserver = mock()

        val sakService = SakServiceImpl(sakRepo)
        sakService.observers.add(observer)
        sakService.opprettSak(mock { on { id } doReturn sakId })

        verify(sakRepo).opprettSak(any())
        verify(sakRepo).hentSak(sak.id)
        verify(observer).handle(argThat { it shouldBe Event.Statistikk.SakOpprettet(sak) })
    }

    @Test
    fun `Publiserer ikke event ved feil av opprettelse av sak`() {
        val sakRepo: SakRepo = mock {
            on { opprettSak(any()) } doThrow RuntimeException("hehe exception")
        }

        val observer: EventObserver = mock()

        val sakService = SakServiceImpl(sakRepo)
        sakService.observers.add(observer)
        assertThrows<RuntimeException> {
            sakService.opprettSak(mock())
            verify(sakRepo).opprettSak(any())
            verifyNoMoreInteractions(sakRepo)
            verifyZeroInteractions(observer)
        }
    }

    @Test
    fun `henter bare åpen søknad på en sak`() {
        val sakRepo: SakRepo = mock {
            on { hentSakRestanser() } doReturn listOf(
                SakRestans(
                    saksnummer = Saksnummer(nummer = 2021),
                    behandlingsId = journalførtSøknadMedOppgave.id,
                    restansType = SakRestans.RestansType.SØKNADSBEHANDLING,
                    status = SakRestans.RestansStatus.NY_SØKNAD,
                    opprettet = journalførtSøknadMedOppgave.opprettet,
                ),
            )
        }

        val sakService = SakServiceImpl(sakRepo)
        val sakMedÅpenSøknad = sakService.hentRestanserForAlleSaker()

        sakMedÅpenSøknad shouldBe listOf(
            SakRestans(
                saksnummer = Saksnummer(nummer = 2021),
                behandlingsId = journalførtSøknadMedOppgave.id,
                restansType = SakRestans.RestansType.SØKNADSBEHANDLING,
                status = SakRestans.RestansStatus.NY_SØKNAD,
                opprettet = journalførtSøknadMedOppgave.opprettet,
            ),
        )
    }

    @Test
    fun `henter bare åpne søknadsbehandlinger på en sak`() {
        val saksnr1 = Saksnummer(2021)
        val saksnr2 = Saksnummer(2022)

        val uavklartSøkandsbehandling = søknadsbehandlingVilkårsvurdertUavklart(saksnr1)
        val underkjentSøknadsbehandling = søknadsbehandlingUnderkjentInnvilget(saksnr1)
        val tilAttesteringSøknadsbehandling = søknadsbehandlingTilAttesteringInnvilget(saksnr2)

        val sakRepo: SakRepo = mock {
            on { hentSakRestanser() } doReturn listOf(
                SakRestans(
                    saksnummer = saksnr1,
                    behandlingsId = uavklartSøkandsbehandling.id,
                    restansType = SakRestans.RestansType.SØKNADSBEHANDLING,
                    status = SakRestans.RestansStatus.UNDER_BEHANDLING,
                    opprettet = uavklartSøkandsbehandling.opprettet,
                ),
                SakRestans(
                    saksnummer = saksnr1,
                    behandlingsId = underkjentSøknadsbehandling.id,
                    restansType = SakRestans.RestansType.SØKNADSBEHANDLING,
                    status = SakRestans.RestansStatus.UNDERKJENT,
                    opprettet = underkjentSøknadsbehandling.opprettet,
                ),
                SakRestans(
                    saksnummer = saksnr2,
                    behandlingsId = tilAttesteringSøknadsbehandling.id,
                    restansType = SakRestans.RestansType.SØKNADSBEHANDLING,
                    status = SakRestans.RestansStatus.TIL_ATTESTERING,
                    opprettet = tilAttesteringSøknadsbehandling.opprettet,
                ),
            )
        }

        val sakService = SakServiceImpl(sakRepo)
        val sakerMedÅpneBehandlinger = sakService.hentRestanserForAlleSaker()

        sakerMedÅpneBehandlinger shouldBe listOf(
            SakRestans(
                saksnummer = saksnr1,
                behandlingsId = uavklartSøkandsbehandling.id,
                restansType = SakRestans.RestansType.SØKNADSBEHANDLING,
                status = SakRestans.RestansStatus.UNDER_BEHANDLING,
                opprettet = uavklartSøkandsbehandling.opprettet,
            ),
            SakRestans(
                saksnummer = saksnr1,
                behandlingsId = underkjentSøknadsbehandling.id,
                restansType = SakRestans.RestansType.SØKNADSBEHANDLING,
                status = SakRestans.RestansStatus.UNDERKJENT,
                opprettet = underkjentSøknadsbehandling.opprettet,
            ),
            SakRestans(
                saksnummer = saksnr2,
                behandlingsId = tilAttesteringSøknadsbehandling.id,
                restansType = SakRestans.RestansType.SØKNADSBEHANDLING,
                status = SakRestans.RestansStatus.TIL_ATTESTERING,
                opprettet = tilAttesteringSøknadsbehandling.opprettet,
            ),
        )
    }

    @Test
    fun `henter bare åpne revurderinger på en sak`() {
        val saknr1 = Saksnummer(2021)
        val saknr2 = Saksnummer(2022)

        val opprettetRevurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(saknr1)
        val simulertRevurdering = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(saknr1)

        val underkjentInnvilgetRevurdering = UnderkjentInnvilgetRevurderingFraInnvilgetSøknadsbehandlignsVedtak(saknr2)
        val tilAttesteringRevurdering = tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(saknr2)

        val sakRepo: SakRepo = mock {
            on { hentSakRestanser() } doReturn listOf(
                SakRestans(
                    saksnummer = saknr1,
                    behandlingsId = opprettetRevurdering.id,
                    restansType = SakRestans.RestansType.REVURDERING,
                    status = SakRestans.RestansStatus.UNDER_BEHANDLING,
                    opprettet = opprettetRevurdering.opprettet,
                ),
                SakRestans(
                    saksnummer = saknr1,
                    behandlingsId = simulertRevurdering.id,
                    restansType = SakRestans.RestansType.REVURDERING,
                    status = SakRestans.RestansStatus.UNDER_BEHANDLING,
                    opprettet = simulertRevurdering.opprettet,
                ),
                SakRestans(
                    saksnummer = saknr2,
                    behandlingsId = underkjentInnvilgetRevurdering.id,
                    restansType = SakRestans.RestansType.REVURDERING,
                    status = SakRestans.RestansStatus.UNDERKJENT,
                    opprettet = underkjentInnvilgetRevurdering.opprettet,
                ),
                SakRestans(
                    saksnummer = saknr2,
                    behandlingsId = tilAttesteringRevurdering.id,
                    restansType = SakRestans.RestansType.REVURDERING,
                    status = SakRestans.RestansStatus.TIL_ATTESTERING,
                    opprettet = tilAttesteringRevurdering.opprettet,
                ),
            )
        }

        val sakService = SakServiceImpl(sakRepo)
        val sakerMedÅpneRevurderinger = sakService.hentRestanserForAlleSaker()

        sakerMedÅpneRevurderinger shouldBe listOf(
            SakRestans(
                saksnummer = saknr1,
                behandlingsId = opprettetRevurdering.id,
                restansType = SakRestans.RestansType.REVURDERING,
                status = SakRestans.RestansStatus.UNDER_BEHANDLING,
                opprettet = opprettetRevurdering.opprettet,
            ),
            SakRestans(
                saksnummer = saknr1,
                behandlingsId = simulertRevurdering.id,
                restansType = SakRestans.RestansType.REVURDERING,
                status = SakRestans.RestansStatus.UNDER_BEHANDLING,
                opprettet = simulertRevurdering.opprettet,
            ),
            SakRestans(
                saksnummer = saknr2,
                behandlingsId = underkjentInnvilgetRevurdering.id,
                restansType = SakRestans.RestansType.REVURDERING,
                status = SakRestans.RestansStatus.UNDERKJENT,
                opprettet = underkjentInnvilgetRevurdering.opprettet,
            ),
            SakRestans(
                saksnummer = saknr2,
                behandlingsId = tilAttesteringRevurdering.id,
                restansType = SakRestans.RestansType.REVURDERING,
                status = SakRestans.RestansStatus.TIL_ATTESTERING,
                opprettet = tilAttesteringRevurdering.opprettet,
            ),
        )
    }
}
