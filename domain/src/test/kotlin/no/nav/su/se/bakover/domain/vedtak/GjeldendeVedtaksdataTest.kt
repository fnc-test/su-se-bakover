package no.nav.su.se.bakover.domain.vedtak

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.april
import no.nav.su.se.bakover.common.extensions.august
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.fixedClock
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.juli
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.extensions.mars
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.Personopplysninger
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattSøknadsbehandling
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.søknad.nySøknadJournalførtMedOppgave
import no.nav.su.se.bakover.test.søknad.søknadinnholdUføre
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdAvslag
import no.nav.su.se.bakover.test.vilkårsvurderingRevurderingIkkeVurdert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class GjeldendeVedtaksdataTest {
    @Test
    fun `finner gjeldende vedtak for gitt dato`() {
        val clock = TikkendeKlokke()
        val (sak, førstegangsvedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(år(2021)),
            clock = clock,
        )
        val revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021))
        val (sak2, revurderingsVedtak) = vedtakRevurdering(
            revurderingsperiode = revurderingsperiode,
            sakOgVedtakSomKanRevurderes = sak to førstegangsvedtak,
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = revurderingsperiode,
                    arbeidsinntekt = 5000.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            clock = clock,
        )
        val data = sak2.kopierGjeldendeVedtaksdata(
            fraOgMed = førstegangsvedtak.periode.fraOgMed,
            clock = clock,
        ).getOrFail()

        data.gjeldendeVedtakPåDato(1.januar(2021)) shouldBe førstegangsvedtak
        data.gjeldendeVedtakPåDato(30.april(2021)) shouldBe førstegangsvedtak
        data.gjeldendeVedtakPåDato(1.mai(2021)) shouldBe revurderingsVedtak
        data.gjeldendeVedtakPåDato(1.desember(2021)) shouldBe revurderingsVedtak
        data.tidslinjeForVedtakErSammenhengende() shouldBe true
        data.garantertSammenhengendePeriode() shouldBe år(2021)
        data.vedtaksperioder() shouldBe listOf(
            Periode.create(1.januar(2021), 30.april(2021)),
            Periode.create(1.mai(2021), 31.desember(2021)),
        )
        data.periodeFørsteTilOgMedSeneste() shouldBe år(2021)
    }

    @Test
    fun `tidslinje inneholder hull mellom to vedtak`() {
        val tikkendeKlokke = TikkendeKlokke(fixedClock)

        val (sak, _, førstegangsvedtak) = iverksattSøknadsbehandlingUføre(
            clock = tikkendeKlokke,
            stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.mars(2021))),
        )

        val (sak2, _, nyStønadsperiode) = iverksattSøknadsbehandlingUføre(
            clock = tikkendeKlokke,
            stønadsperiode = Stønadsperiode.create(Periode.create(1.mai(2021), 31.desember(2021))),
            sakOgSøknad = sak to nySøknadJournalførtMedOppgave(
                clock = tikkendeKlokke,
                sakId = sak.id,
                søknadInnhold = søknadinnholdUføre(
                    personopplysninger = Personopplysninger(sak.fnr),
                ),
            ),
        )
        val data = sak2.kopierGjeldendeVedtaksdata(
            fraOgMed = førstegangsvedtak.periode.fraOgMed,
            clock = tikkendeKlokke,
        ).getOrFail()

        data.gjeldendeVedtakPåDato(1.mars(2021)) shouldBe førstegangsvedtak
        data.gjeldendeVedtakPåDato(1.april(2021)) shouldBe null
        data.gjeldendeVedtakPåDato(1.desember(2021)) shouldBe nyStønadsperiode
        data.tidslinjeForVedtakErSammenhengende() shouldBe false
        assertThrows<IllegalStateException> {
            data.garantertSammenhengendePeriode()
        }
        data.vedtaksperioder() shouldBe listOf(
            Periode.create(1.januar(2021), 31.mars(2021)),
            Periode.create(1.mai(2021), 31.desember(2021)),
        )
        data.periodeFørsteTilOgMedSeneste() shouldBe år(2021)
    }

    @Test
    fun `håndterer at etterspurt periode ikke inneholder vedtaksdata`() {
        val (_, førstegangsvedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.mars(2021))),
        )
        val data = GjeldendeVedtaksdata(
            periode = Periode.create(1.mai(2021), 31.desember(2021)),
            vedtakListe = nonEmptyListOf(førstegangsvedtak),
            clock = fixedClock,
        )
        data.gjeldendeVedtakPåDato(1.mai(2021)) shouldBe null
        data.grunnlagsdata shouldBe Grunnlagsdata.IkkeVurdert
        data.vilkårsvurderinger shouldBe vilkårsvurderingRevurderingIkkeVurdert()
        assertThrows<IllegalStateException> {
            data.garantertSammenhengendePeriode()
        }
        data.vedtaksperioder() shouldBe emptyList()
        assertThrows<NoSuchElementException> {
            data.periodeFørsteTilOgMedSeneste()
        }
    }

    @Test
    fun `tidslinje inneholder bare et vedtak`() {
        val (_, førstegangsvedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(år(2021)),
        )

        val data = GjeldendeVedtaksdata(
            periode = år(2021),
            vedtakListe = nonEmptyListOf(førstegangsvedtak),
            clock = fixedClock,
        )
        data.tidslinjeForVedtakErSammenhengende() shouldBe true
    }

    @Test
    fun `gjeldende vedtaksdata inneholder utbetalinger som skal avkortes`() {
        val (sak, _) = vedtakRevurdering(
            clock = TikkendeKlokke(1.august(2021).fixedClock()),
            revurderingsperiode = år(2021),
            vilkårOverrides = listOf(
                utenlandsoppholdAvslag(
                    periode = år(2021),
                ),
            ),
            utbetalingerKjørtTilOgMed = { 1.juli(2021) },
        )

        GjeldendeVedtaksdata(
            periode = år(2021),
            vedtakListe = sak.vedtakListe.filterIsInstance<VedtakSomKanRevurderes>().toNonEmptyList(),
            clock = fixedClock,
        ).let {
            it.inneholderOpphørsvedtakMedAvkortingUtenlandsopphold() shouldBe true
            it.pågåendeAvkortingEllerBehovForFremtidigAvkorting shouldBe true
        }
    }

    @Test
    fun `gjeldendeVedtakMånedsvis - mapper riktig med hull`() {
        val revurderingsperiode = januar(2021)..februar(2021)
        val clock = TikkendeKlokke(1.februar(2021).fixedClock())
        val (sak, revurderingsvedtak) = vedtakRevurdering(
            clock = clock,
            revurderingsperiode = revurderingsperiode,
            stønadsperiode = Stønadsperiode.create(januar(2021)..april(2021)),
            vilkårOverrides = listOf(
                utenlandsoppholdAvslag(
                    periode = revurderingsperiode,
                ),
            ),
        )
        val søknadsbehandlingsvedtak1 =
            sak.vedtakListe.first() as VedtakInnvilgetSøknadsbehandling
        val (oppdatertSak, _, nyttSøknadsbehandlingsvedtak) = iverksattSøknadsbehandling(
            // Hopper over mai for å lage et hull
            stønadsperiode = Stønadsperiode.create(juni(2021)),
            sakOgSøknad = sak to nySøknadJournalførtMedOppgave(sakId = sak.id, fnr = sak.fnr, clock = clock),
            clock = clock,
        )
        GjeldendeVedtaksdata(
            periode = år(2021),
            vedtakListe = oppdatertSak.vedtakListe.filterIsInstance<VedtakSomKanRevurderes>().toNonEmptyList(),
            clock = clock,
        ).let {
            it.gjeldendeVedtakMånedsvisMedPotensielleHull() shouldBe mapOf(
                januar(2021) to revurderingsvedtak,
                februar(2021) to revurderingsvedtak,
                mars(2021) to søknadsbehandlingsvedtak1,
                april(2021) to søknadsbehandlingsvedtak1,
                juni(2021) to nyttSøknadsbehandlingsvedtak,
            )
        }
    }
}
