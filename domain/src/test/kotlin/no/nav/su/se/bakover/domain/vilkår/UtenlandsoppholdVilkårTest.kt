package no.nav.su.se.bakover.domain.vilkår

import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.juli
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.grunnlag.Utenlandsoppholdgrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import org.junit.jupiter.api.Test
import java.util.UUID

internal class UtenlandsoppholdVilkårTest {

    @Test
    fun `oppdaterer periode på vurderingsperioder og grunnlag`() {
        UtenlandsoppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(
                VurderingsperiodeUtenlandsopphold.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(fixedClock),
                    resultat = Resultat.Innvilget,
                    grunnlag = Utenlandsoppholdgrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        periode = år(2021),
                    ),
                    vurderingsperiode = år(2021),
                    begrunnelse = "jabadoo",
                ).getOrFail(),
            ),
        ).getOrFail().oppdaterStønadsperiode(Stønadsperiode.create(juli(2021), "")).erLik(
            UtenlandsoppholdVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeUtenlandsopphold.tryCreate(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        resultat = Resultat.Innvilget,
                        grunnlag = Utenlandsoppholdgrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(fixedClock),
                            periode = juli(2021),
                        ),
                        vurderingsperiode = juli(2021),
                        begrunnelse = "jabadoo",
                    ).getOrFail(),
                ),
            ).getOrFail(),
        )
    }

    @Test
    fun `godtar ikke overlappende vurderingsperioder`() {
        UtenlandsoppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(
                VurderingsperiodeUtenlandsopphold.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(fixedClock),
                    resultat = Resultat.Innvilget,
                    grunnlag = Utenlandsoppholdgrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        periode = år(2021),
                    ),
                    vurderingsperiode = år(2021),
                    begrunnelse = "jabadoo",
                ).getOrFail(),
                VurderingsperiodeUtenlandsopphold.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(fixedClock),
                    resultat = Resultat.Innvilget,
                    grunnlag = Utenlandsoppholdgrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        periode = år(2021),
                    ),
                    vurderingsperiode = år(2021),
                    begrunnelse = "jabadoo",
                ).getOrFail(),
            ),
        ) shouldBe UtenlandsoppholdVilkår.Vurdert.UgyldigUtenlandsoppholdVilkår.OverlappendeVurderingsperioder.left()
    }

    @Test
    fun `lager tidslinje for vilkår, vurderingsperioder og grunnlag`() {
        val v1 = VurderingsperiodeUtenlandsopphold.tryCreate(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            resultat = Resultat.Innvilget,
            grunnlag = Utenlandsoppholdgrunnlag(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(fixedClock),
                periode = mai(2021),
            ),
            vurderingsperiode = mai(2021),
            begrunnelse = "jabadoo",
        ).getOrFail()

        val v2 = VurderingsperiodeUtenlandsopphold.tryCreate(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            resultat = Resultat.Innvilget,
            grunnlag = Utenlandsoppholdgrunnlag(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(fixedClock),
                periode = juni(2021),
            ),
            vurderingsperiode = juni(2021),
            begrunnelse = null,
        ).getOrFail()

        UtenlandsoppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(v1, v2),
        ).getOrFail()
            .lagTidslinje(mai(2021))
            .erLik(UtenlandsoppholdVilkår.Vurdert.tryCreate(vurderingsperioder = nonEmptyListOf(v1)).getOrFail())

        UtenlandsoppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(v1, v2),
        ).getOrFail()
            .lagTidslinje(juni(2021))
            .erLik(UtenlandsoppholdVilkår.Vurdert.tryCreate(vurderingsperioder = nonEmptyListOf(v2)).getOrFail())

        UtenlandsoppholdVilkår.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(v1, v2),
        ).getOrFail()
            .lagTidslinje(år(2021))
            .erLik(UtenlandsoppholdVilkår.Vurdert.tryCreate(vurderingsperioder = nonEmptyListOf(v1, v2)).getOrFail())
    }
}
