package no.nav.su.se.bakover.domain.vilkår

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.grunnlag.PersonligOppmøteGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import org.junit.jupiter.api.Test
import java.util.UUID

internal class VurderingsperiodePersonligOppmøteTest {
    private val vilkårId = UUID.randomUUID()
    private val grunnlagId = UUID.randomUUID()

    @Test
    fun `oppdaterer periode`() {
        VurderingsperiodePersonligOppmøte.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = PersonligOppmøteGrunnlag(
                id = grunnlagId,
                opprettet = fixedTidspunkt,
                periode = år(2021),
            ),
            vurderingsperiode = år(2021),
            begrunnelse = null,
        ).getOrFail()
            .let {
                it.oppdaterStønadsperiode(
                    Stønadsperiode.create(
                        februar(2021),
                        "",
                    ),
                ) shouldBe VurderingsperiodePersonligOppmøte.tryCreate(
                    id = vilkårId,
                    opprettet = fixedTidspunkt,
                    resultat = Resultat.Innvilget,
                    grunnlag = PersonligOppmøteGrunnlag(
                        id = grunnlagId,
                        opprettet = fixedTidspunkt,
                        periode = februar(2021),
                    ),
                    vurderingsperiode = februar(2021),
                    begrunnelse = null,
                ).getOrFail()
            }
    }

    @Test
    fun `kopierer korrekte verdier`() {
        VurderingsperiodePersonligOppmøte.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = PersonligOppmøteGrunnlag(
                id = grunnlagId,
                opprettet = fixedTidspunkt,
                periode = år(2021),
            ),
            vurderingsperiode = år(2021),
            begrunnelse = null,
        ).getOrFail()
            .copy(CopyArgs.Tidslinje.Full).let {
                it shouldBe it.copy()
            }

        VurderingsperiodePersonligOppmøte.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = PersonligOppmøteGrunnlag(
                id = grunnlagId,
                opprettet = fixedTidspunkt,
                periode = år(2021),
            ),
            vurderingsperiode = år(2021),
            begrunnelse = null,
        ).getOrFail().copy(CopyArgs.Tidslinje.NyPeriode(mai(2021))).let {
            it shouldBe it.copy(periode = mai(2021))
        }
    }

    @Test
    fun `er lik ser kun på funksjonelle verdier`() {
        VurderingsperiodePersonligOppmøte.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = PersonligOppmøteGrunnlag(
                id = grunnlagId,
                opprettet = fixedTidspunkt,
                periode = år(2021),
            ),
            vurderingsperiode = år(2021),
            begrunnelse = null,
        ).getOrFail()
            .erLik(
                VurderingsperiodePersonligOppmøte.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    resultat = Resultat.Innvilget,
                    grunnlag = PersonligOppmøteGrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(),
                        periode = februar(2021),
                    ),
                    vurderingsperiode = februar(2021),
                    begrunnelse = "koko",
                ).getOrFail(),
            ) shouldBe true

        VurderingsperiodePersonligOppmøte.tryCreate(
            id = vilkårId,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = PersonligOppmøteGrunnlag(
                id = grunnlagId,
                opprettet = fixedTidspunkt,
                periode = år(2021),
            ),
            vurderingsperiode = år(2021),
            begrunnelse = null,
        ).getOrFail()
            .erLik(
                VurderingsperiodePersonligOppmøte.tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    resultat = Resultat.Avslag,
                    grunnlag = PersonligOppmøteGrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(),
                        periode = februar(2021),
                    ),
                    vurderingsperiode = februar(2021),
                    begrunnelse = "koko",
                ).getOrFail(),
            ) shouldBe false
    }
}
