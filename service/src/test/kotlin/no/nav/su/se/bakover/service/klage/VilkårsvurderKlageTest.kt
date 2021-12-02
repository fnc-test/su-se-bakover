package no.nav.su.se.bakover.service.klage

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KunneIkkeVilkårsvurdereKlage
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

internal class VilkårsvurderKlageTest {

    @Test
    fun `fant ikke klage`() {

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn null
            },
        )
        val klageId = UUID.randomUUID()
        val request = VurderKlagevilkårRequest(
            saksbehandler = NavIdentBruker.Saksbehandler("s2"),
            klageId = klageId,
            vedtakId = null,
            innenforFristen = null,
            klagesDetPåKonkreteElementerIVedtaket = null,
            erUnderskrevet = null,
            begrunnelse = null,
        )
        mocks.service.vilkårsvurder(request) shouldBe KunneIkkeVilkårsvurdereKlage.FantIkkeKlage.left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klageId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `fant ikke vedtak`() {

        val mocks = KlageServiceMocks(
            vedtakRepoMock = mock {
                on { hentForVedtakId(any()) } doReturn null
            },
        )
        val vedtakId = UUID.randomUUID()
        val request = VurderKlagevilkårRequest(
            saksbehandler = NavIdentBruker.Saksbehandler("nySaksbehandler"),
            klageId = UUID.randomUUID(),
            vedtakId = vedtakId,
            innenforFristen = null,
            klagesDetPåKonkreteElementerIVedtaket = null,
            erUnderskrevet = null,
            begrunnelse = null,
        )
        mocks.service.vilkårsvurder(request) shouldBe KunneIkkeVilkårsvurdereKlage.FantIkkeVedtak.left()

        verify(mocks.vedtakRepoMock).hentForVedtakId(vedtakId)
        mocks.verifyNoMoreInteractions()
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
        val request = VurderKlagevilkårRequest(
            saksbehandler = NavIdentBruker.Saksbehandler("nySaksbehandler"),
            klageId = klage.id,
            vedtakId = null,
            innenforFristen = null,
            klagesDetPåKonkreteElementerIVedtaket = null,
            erUnderskrevet = null,
            begrunnelse = null,
        )
        mocks.service.vilkårsvurder(request) shouldBe KunneIkkeVilkårsvurdereKlage.UgyldigTilstand(
            klage::class,
            VilkårsvurdertKlage::class,
        ).left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Skal kunne vilkårsvurdere opprettet klage`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        val opprettetKlage = opprettetKlage(
            sakId = sak.id,
        )
        verifiserGyldigStatusovergangTilPåbegynt(
            vedtak = vedtak,
            klage = opprettetKlage,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            vedtak = vedtak,
            klage = opprettetKlage,
        )
    }

    @Test
    fun `Skal kunne vilkårsvurdere påbegynt vilkårsvurdert klage`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        val påbegyntVilkårsvurdertKlage = påbegyntVilkårsvurdertKlage(
            sakId = sak.id,
            vedtakId = vedtak.id,
        )
        verifiserGyldigStatusovergangTilPåbegynt(
            vedtak = vedtak,
            klage = påbegyntVilkårsvurdertKlage,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            vedtak = vedtak,
            klage = påbegyntVilkårsvurdertKlage,
        )
    }

    @Test
    fun `Skal kunne vilkårsvurdere utfylt vilkårsvurdert klage`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        val utfyltVilkårsvurdertKlage = utfyltVilkårsvurdertKlage(
            sakId = sak.id,
            vedtakId = vedtak.id,
        )
        verifiserGyldigStatusovergangTilPåbegynt(
            vedtak = vedtak,
            klage = utfyltVilkårsvurdertKlage,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            vedtak = vedtak,
            klage = utfyltVilkårsvurdertKlage,
        )
    }

    @Test
    fun `Skal kunne vilkårsvurdere bekreftet vilkårsvurdert klage`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        val bekreftetVilkårsvurdertKlage = bekreftetVilkårsvurdertKlage(
            sakId = sak.id,
            vedtakId = vedtak.id,
        )
        verifiserGyldigStatusovergangTilPåbegynt(
            vedtak = vedtak,
            klage = bekreftetVilkårsvurdertKlage,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            vedtak = vedtak,
            klage = bekreftetVilkårsvurdertKlage,
        )
    }

    @Test
    fun `Skal kunne vilkårsvurdere påbegynt vurdert klage`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        val påbegyntVurdertKlage = påbegyntVurdertKlage(
            sakId = sak.id,
            vedtakId = vedtak.id,
        )
        verifiserGyldigStatusovergangTilPåbegynt(
            vedtak = vedtak,
            klage = påbegyntVurdertKlage,
            vurderingerTilKlage = påbegyntVurdertKlage.vurderinger,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            vedtak = vedtak,
            klage = påbegyntVurdertKlage,
            vurderingerTilKlage = påbegyntVurdertKlage.vurderinger,
        )
    }

    @Test
    fun `Skal kunne vilkårsvurdere utfylt vurdert klage`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        val utfyltVurdertKlage = utfyltVurdertKlage(
            sakId = sak.id,
            vedtakId = vedtak.id,
        )
        verifiserGyldigStatusovergangTilPåbegynt(
            vedtak = vedtak,
            klage = utfyltVurdertKlage,
            vurderingerTilKlage = utfyltVurdertKlage.vurderinger,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            vedtak = vedtak,
            klage = utfyltVurdertKlage,
            vurderingerTilKlage = utfyltVurdertKlage.vurderinger,
        )
    }

    @Test
    fun `Skal kunne vilkårsvurdere bekreftet vurdert klage`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        val bekreftetVurdertKlage = bekreftetVurdertKlage(
            sakId = sak.id,
            vedtakId = vedtak.id,
        )
        verifiserGyldigStatusovergangTilPåbegynt(
            vedtak = vedtak,
            klage = bekreftetVurdertKlage,
            vurderingerTilKlage = bekreftetVurdertKlage.vurderinger,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            vedtak = vedtak,
            klage = bekreftetVurdertKlage,
            vurderingerTilKlage = bekreftetVurdertKlage.vurderinger,
        )
    }

    @Test
    fun `Skal kunne vilkårsvurdere underkjent klage`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        val underkjentKlage = underkjentKlage(
            sakId = sak.id,
            vedtakId = vedtak.id,
        )
        verifiserGyldigStatusovergangTilPåbegynt(
            vedtak = vedtak,
            klage = underkjentKlage,
            vurderingerTilKlage = underkjentKlage.vurderinger,
            attesteringer = underkjentKlage.attesteringer,
        )
        verifiserGyldigStatusovergangTilUtfylt(
            vedtak = vedtak,
            klage = underkjentKlage,
            vurderingerTilKlage = underkjentKlage.vurderinger,
            attesteringer = underkjentKlage.attesteringer,
        )
    }

    private fun verifiserGyldigStatusovergangTilPåbegynt(
        vedtak: Vedtak,
        klage: Klage,
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

        val request = VurderKlagevilkårRequest(
            saksbehandler = NavIdentBruker.Saksbehandler("nySaksbehandler"),
            klageId = klage.id,
            vedtakId = null,
            innenforFristen = null,
            klagesDetPåKonkreteElementerIVedtaket = null,
            erUnderskrevet = null,
            begrunnelse = null,
        )

        var expectedKlage: VilkårsvurdertKlage.Påbegynt?
        mocks.service.vilkårsvurder(request).orNull()!!.also {
            expectedKlage = VilkårsvurdertKlage.Påbegynt.create(
                id = it.id,
                opprettet = fixedTidspunkt,
                sakId = klage.sakId,
                journalpostId = klage.journalpostId,
                saksbehandler = NavIdentBruker.Saksbehandler("nySaksbehandler"),
                vilkårsvurderinger = VilkårsvurderingerTilKlage.empty(),
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

    private fun verifiserGyldigStatusovergangTilUtfylt(
        vedtak: Vedtak,
        klage: Klage,
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
        val request = VurderKlagevilkårRequest(
            saksbehandler = NavIdentBruker.Saksbehandler("nySaksbehandler"),
            klageId = klage.id,
            vedtakId = vedtak.id,
            innenforFristen = true,
            klagesDetPåKonkreteElementerIVedtaket = true,
            erUnderskrevet = true,
            begrunnelse = "SomeBegrunnelse",
        )
        var expectedKlage: VilkårsvurdertKlage.Utfylt?
        mocks.service.vilkårsvurder(request).orNull()!!.also {
            expectedKlage = VilkårsvurdertKlage.Utfylt.create(
                id = it.id,
                opprettet = fixedTidspunkt,
                sakId = klage.sakId,
                journalpostId = klage.journalpostId,
                saksbehandler = NavIdentBruker.Saksbehandler("nySaksbehandler"),
                vilkårsvurderinger = VilkårsvurderingerTilKlage.Utfylt(
                    vedtakId = it.vilkårsvurderinger.vedtakId!!,
                    innenforFristen = true,
                    klagesDetPåKonkreteElementerIVedtaket = true,
                    erUnderskrevet = true,
                    begrunnelse = "SomeBegrunnelse",
                ),
                vurderinger = vurderingerTilKlage,
                attesteringer = attesteringer,
                datoKlageMottatt = 1.desember(2021),
            )
            it shouldBe expectedKlage
        }

        verify(mocks.vedtakRepoMock).hentForVedtakId(vedtak.id)
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.klageRepoMock).lagre(
            argThat {
                it shouldBe expectedKlage
            },
        )
        mocks.verifyNoMoreInteractions()
    }
}
