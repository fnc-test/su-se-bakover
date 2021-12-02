package no.nav.su.se.bakover.service.klage

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KunneIkkeBekrefteKlagesteg
import no.nav.su.se.bakover.domain.klage.VilkårsvurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurderingerTilKlage
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.bekreftetVilkårsvurdertKlage
import no.nav.su.se.bakover.test.bekreftetVurdertKlage
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.iverksattKlage
import no.nav.su.se.bakover.test.klageTilAttestering
import no.nav.su.se.bakover.test.opprettetKlage
import no.nav.su.se.bakover.test.påbegyntVilkårsvurdertKlage
import no.nav.su.se.bakover.test.påbegyntVurdertKlage
import no.nav.su.se.bakover.test.underkjentKlage
import no.nav.su.se.bakover.test.utfyltVilkårsvurdertKlage
import no.nav.su.se.bakover.test.utfyltVurdertKlage
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

internal class BekreftVilkårsvurdertKlageTest {

    @Test
    fun `fant ikke klage`() {

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn null
            },
        )
        val klageId = UUID.randomUUID()
        val saksbehandler = NavIdentBruker.Saksbehandler("s2")
        mocks.service.bekreftVilkårsvurderinger(
            klageId = klageId,
            saksbehandler = saksbehandler,
        ) shouldBe KunneIkkeBekrefteKlagesteg.FantIkkeKlage.left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klageId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Ugyldig tilstandsovergang fra opprettet`() {
        verifiserUgyldigTilstandsovergang(
            klage = opprettetKlage(),
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra påbegynt vilkårsvurdering`() {
        verifiserUgyldigTilstandsovergang(
            klage = påbegyntVilkårsvurdertKlage(),
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra til attestering`() {
        verifiserUgyldigTilstandsovergang(
            klage = klageTilAttestering(),
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra iverksatt`() {
        verifiserUgyldigTilstandsovergang(
            klage = iverksattKlage(),
        )
    }

    private fun verifiserUgyldigTilstandsovergang(
        klage: Klage,
    ) {
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
            },
        )
        mocks.service.bekreftVilkårsvurderinger(
            klageId = klage.id,
            saksbehandler = klage.saksbehandler,
        ) shouldBe KunneIkkeBekrefteKlagesteg.UgyldigTilstand(klage::class, VilkårsvurdertKlage.Bekreftet::class).left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Skal kunne bekrefte utfylt vilkårsvurdering`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        val utfyltVilkårsvurdertKlage = utfyltVilkårsvurdertKlage(
            sakId = sak.id,
            vedtakId = vedtak.id,
        )
        verifiserGyldigStatusovergang(
            vedtak = vedtak,
            klage = utfyltVilkårsvurdertKlage,
            vilkårsvurderingerTilKlage = utfyltVilkårsvurdertKlage.vilkårsvurderinger,
        )
    }

    @Test
    fun `Skal kunne bekrefte bekreftet vilkårsvurdering`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        val utfyltVilkårsvurdertKlage = bekreftetVilkårsvurdertKlage(
            sakId = sak.id,
            vedtakId = vedtak.id,
        )
        verifiserGyldigStatusovergang(
            vedtak = vedtak,
            klage = utfyltVilkårsvurdertKlage,
            vilkårsvurderingerTilKlage = utfyltVilkårsvurdertKlage.vilkårsvurderinger,
        )
    }

    @Test
    fun `Skal kunne bekrefte påbegynt vurdert klage`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        val utfyltVilkårsvurdertKlage = påbegyntVurdertKlage(
            sakId = sak.id,
            vedtakId = vedtak.id,
        )
        verifiserGyldigStatusovergang(
            vedtak = vedtak,
            klage = utfyltVilkårsvurdertKlage,
            vilkårsvurderingerTilKlage = utfyltVilkårsvurdertKlage.vilkårsvurderinger,
            vurderingerTilKlage = utfyltVilkårsvurdertKlage.vurderinger,
        )
    }

    @Test
    fun `Skal kunne bekrefte utfylt vurdert klage`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        val utfyltVilkårsvurdertKlage = utfyltVurdertKlage(
            sakId = sak.id,
            vedtakId = vedtak.id,
        )
        verifiserGyldigStatusovergang(
            vedtak = vedtak,
            klage = utfyltVilkårsvurdertKlage,
            vilkårsvurderingerTilKlage = utfyltVilkårsvurdertKlage.vilkårsvurderinger,
            vurderingerTilKlage = utfyltVilkårsvurdertKlage.vurderinger,
        )
    }

    @Test
    fun `Skal kunne bekrefte bekreftet vurdert klage`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        val utfyltVilkårsvurdertKlage = bekreftetVurdertKlage(
            sakId = sak.id,
            vedtakId = vedtak.id,
        )
        verifiserGyldigStatusovergang(
            vedtak = vedtak,
            klage = utfyltVilkårsvurdertKlage,
            vilkårsvurderingerTilKlage = utfyltVilkårsvurdertKlage.vilkårsvurderinger,
            vurderingerTilKlage = utfyltVilkårsvurdertKlage.vurderinger,
        )
    }

    @Test
    fun `Skal kunne bekrefte underkjent klage`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        val utfyltVilkårsvurdertKlage = underkjentKlage(
            sakId = sak.id,
            vedtakId = vedtak.id,
        )
        verifiserGyldigStatusovergang(
            vedtak = vedtak,
            klage = utfyltVilkårsvurdertKlage,
            vilkårsvurderingerTilKlage = utfyltVilkårsvurdertKlage.vilkårsvurderinger,
            vurderingerTilKlage = utfyltVilkårsvurdertKlage.vurderinger,
            attesteringer = utfyltVilkårsvurdertKlage.attesteringer,
        )
    }

    private fun verifiserGyldigStatusovergang(
        vedtak: Vedtak,
        klage: Klage,
        vilkårsvurderingerTilKlage: VilkårsvurderingerTilKlage.Utfylt,
        vurderingerTilKlage: VurderingerTilKlage? = null,
        attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty(),
    ) {

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
            },
            vedtakRepoMock = mock {
                on { hentForVedtakId(any()) } doReturn vedtak
            },
        )

        var expectedKlage: VilkårsvurdertKlage.Bekreftet?
        mocks.service.bekreftVilkårsvurderinger(
            klageId = klage.id,
            saksbehandler = NavIdentBruker.Saksbehandler("bekreftetVilkårsvurderingene"),
        ).orNull()!!.also {
            expectedKlage = VilkårsvurdertKlage.Bekreftet.create(
                id = it.id,
                opprettet = fixedTidspunkt,
                sakId = klage.sakId,
                journalpostId = klage.journalpostId,
                saksbehandler = NavIdentBruker.Saksbehandler("bekreftetVilkårsvurderingene"),
                vilkårsvurderinger = vilkårsvurderingerTilKlage,
                vurderinger = vurderingerTilKlage,
                attesteringer = attesteringer,
                datoKlageMottatt = 1.desember(2021),
            )
            it shouldBe expectedKlage
        }

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.klageRepoMock).lagre(
            argThat {
                it shouldBe expectedKlage
            },
        )
        mocks.verifyNoMoreInteractions()
    }
}
