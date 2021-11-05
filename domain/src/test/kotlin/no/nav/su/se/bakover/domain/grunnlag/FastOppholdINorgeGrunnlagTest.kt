package no.nav.su.se.bakover.domain.grunnlag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.test.periodeFebruar2021
import no.nav.su.se.bakover.test.periodeMai2021
import org.junit.jupiter.api.Test
import java.util.UUID

internal class FastOppholdINorgeGrunnlagTest {
    @Test
    fun `oppdaterer periode`() {
        FastOppholdINorgeGrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = periode2021,
        ).let {
            it.oppdaterPeriode(periodeFebruar2021) shouldBe FastOppholdINorgeGrunnlag(
                id = it.id,
                opprettet = it.opprettet,
                periode = periodeFebruar2021,
            )
        }
    }

    @Test
    fun `kopierer korrekte verdier`() {
        FastOppholdINorgeGrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = periode2021,
        ).copy(CopyArgs.Tidslinje.Full).let {
            it shouldBe it.copy()
        }

        FastOppholdINorgeGrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = periode2021,
        ).copy(CopyArgs.Tidslinje.NyPeriode(periodeMai2021)).let {
            it shouldBe it.copy(periode = periodeMai2021)
        }
    }

    @Test
    fun `er lik ser kun på funksjonelle verdier`() {
        FastOppholdINorgeGrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = periode2021,
        ).erLik(
            FastOppholdINorgeGrunnlag(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                periode = periodeFebruar2021,
            ),
        ) shouldBe true
    }
}