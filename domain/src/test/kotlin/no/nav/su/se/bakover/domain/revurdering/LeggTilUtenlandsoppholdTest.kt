package no.nav.su.se.bakover.domain.revurdering

import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeUtenlandsopphold
import no.nav.su.se.bakover.test.avsluttetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.beregnetRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.iverksattRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.månedsperiodeJanuar2020
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.periodeFebruar2021
import no.nav.su.se.bakover.test.periodeJanuar2021
import no.nav.su.se.bakover.test.simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.simulertRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.tilAttesteringRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.underkjentInnvilgetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.utenlandsoppholdInnvilget
import org.junit.jupiter.api.Test

class LeggTilUtenlandsoppholdTest {

    @Test
    fun `får ikke legge til opphold i utlandet utenfor perioden`() {
        val uavklart = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().second

        uavklart.oppdaterUtenlandsoppholdOgMarkerSomVurdert(
            utenlandsopphold = utenlandsoppholdInnvilget(
                periode = månedsperiodeJanuar2020,
            ),
        ) shouldBe Revurdering.KunneIkkeLeggeTilUtenlandsopphold.VurderingsperiodeUtenforBehandlingsperiode.left()

        uavklart.oppdaterUtenlandsoppholdOgMarkerSomVurdert(
            utenlandsopphold = UtenlandsoppholdVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeUtenlandsopphold.create(
                        opprettet = fixedTidspunkt,
                        resultat = Resultat.Innvilget,
                        grunnlag = null,
                        periode = uavklart.periode,
                        begrunnelse = "begrunnelse",
                    ),
                    VurderingsperiodeUtenlandsopphold.create(
                        opprettet = fixedTidspunkt,
                        resultat = Resultat.Innvilget,
                        grunnlag = null,
                        periode = månedsperiodeJanuar2020,
                        begrunnelse = "begrunnelse",
                    ),
                ),
            ).getOrFail(),
        ) shouldBe Revurdering.KunneIkkeLeggeTilUtenlandsopphold.VurderingsperiodeUtenforBehandlingsperiode.left()

        uavklart.oppdaterUtenlandsoppholdOgMarkerSomVurdert(
            utenlandsopphold = utenlandsoppholdInnvilget(
                periode = uavklart.periode,
            ),
        ).isRight() shouldBe true
    }

    @Test
    fun `får bare lagt til opphold i utlandet for enkelte typer`() {
        listOf(
            opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(),
            // @Disabled("https://trello.com/c/5iblmYP9/1090-endre-sperre-for-10-endring-til-%C3%A5-v%C3%A6re-en-advarsel")
            // beregnetRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(),
            beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(),
            beregnetRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(),
            simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(),
            simulertRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(),
            underkjentInnvilgetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(),
        ).map {
            it.second
        }.forEach {
            it.oppdaterUtenlandsoppholdOgMarkerSomVurdert(utenlandsoppholdInnvilget()).let { oppdatert ->
                oppdatert.isRight() shouldBe true
                oppdatert.getOrFail() shouldBe beOfType<OpprettetRevurdering>()
            }
        }

        listOf(
            // @Disabled("https://trello.com/c/5iblmYP9/1090-endre-sperre-for-10-endring-til-%C3%A5-v%C3%A6re-en-advarsel")
            // tilAttesteringRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(),
            tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(),
            tilAttesteringRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(),
            // @Disabled("https://trello.com/c/5iblmYP9/1090-endre-sperre-for-10-endring-til-%C3%A5-v%C3%A6re-en-advarsel")
            // iverksattRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(),
            iverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(),
            iverksattRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(),
            avsluttetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(),
        ).map {
            it.second
        }.forEach {
            it.oppdaterUtenlandsoppholdOgMarkerSomVurdert(
                utenlandsoppholdInnvilget(),
            ) shouldBe Revurdering.KunneIkkeLeggeTilUtenlandsopphold.UgyldigTilstand(
                it::class,
                OpprettetRevurdering::class,
            ).left()
        }
    }

    @Test
    fun `får ikke legge til vurderingsperioder med både avslag og innvilget`() {
        val uavklart = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().second

        uavklart.oppdaterUtenlandsoppholdOgMarkerSomVurdert(
            utenlandsopphold = UtenlandsoppholdVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeUtenlandsopphold.create(
                        opprettet = fixedTidspunkt,
                        resultat = Resultat.Innvilget,
                        grunnlag = null,
                        periode = periodeJanuar2021,
                        begrunnelse = "begrunnelse",
                    ),
                    VurderingsperiodeUtenlandsopphold.create(
                        opprettet = fixedTidspunkt,
                        resultat = Resultat.Avslag,
                        grunnlag = null,
                        periode = Periode.create(1.februar(2021), 31.desember(2021)),
                        begrunnelse = "begrunnelse",
                    ),
                ),
            ).getOrFail(),
        ) shouldBe Revurdering.KunneIkkeLeggeTilUtenlandsopphold.AlleVurderingsperioderMåHaSammeResultat.left()
    }

    @Test
    fun `må vurdere hele revurderingsperioden`() {
        val uavklart = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().second

        uavklart.oppdaterUtenlandsoppholdOgMarkerSomVurdert(
            utenlandsopphold = UtenlandsoppholdVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeUtenlandsopphold.create(
                        opprettet = fixedTidspunkt,
                        resultat = Resultat.Innvilget,
                        grunnlag = null,
                        periode = periodeJanuar2021,
                        begrunnelse = "begrunnelse",
                    ),
                    VurderingsperiodeUtenlandsopphold.create(
                        opprettet = fixedTidspunkt,
                        resultat = Resultat.Innvilget,
                        grunnlag = null,
                        periode = periodeFebruar2021,
                        begrunnelse = "begrunnelse",
                    ),
                ),
            ).getOrFail(),
        ) shouldBe Revurdering.KunneIkkeLeggeTilUtenlandsopphold.MåVurdereHelePerioden.left()
    }
}
