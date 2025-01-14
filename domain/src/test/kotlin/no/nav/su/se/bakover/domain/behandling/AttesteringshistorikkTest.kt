package no.nav.su.se.bakover.domain.behandling

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.attestering.UnderkjennAttesteringsgrunnBehandling
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import java.time.temporal.ChronoUnit

internal class AttesteringshistorikkTest {

    @Test
    fun `attesteringer er sortert etter tidspunkt`() {
        val attestering1 = Attestering.Underkjent(
            attestant = NavIdentBruker.Attestant("Attestant1"),
            opprettet = fixedTidspunkt,
            grunn = UnderkjennAttesteringsgrunnBehandling.ANDRE_FORHOLD,
            kommentar = "kommentar",
        )
        val attestering2 = Attestering.Iverksatt(
            attestant = NavIdentBruker.Attestant("Attestant2"),
            opprettet = fixedTidspunkt.plus(2, ChronoUnit.DAYS),
        )

        Attesteringshistorikk.create(mutableListOf(attestering2, attestering1)) shouldBe listOf(
            attestering1,
            attestering2,
        )
    }

    @Test
    fun `legger till attestering i sluttet av listen`() {
        val opprettet = fixedTidspunkt
        val attestering1 = Attestering.Underkjent(
            attestant = NavIdentBruker.Attestant("Attestant2"),
            opprettet = opprettet.plus(1, ChronoUnit.DAYS),
            grunn = UnderkjennAttesteringsgrunnBehandling.BEREGNINGEN_ER_FEIL,
            kommentar = "kommentar",
        )
        val attestering2 = Attestering.Underkjent(
            attestant = NavIdentBruker.Attestant("Attestant2"),
            opprettet = opprettet.plus(2, ChronoUnit.DAYS),
            grunn = UnderkjennAttesteringsgrunnBehandling.BEREGNINGEN_ER_FEIL,
            kommentar = "kommentar",
        )
        val attestering3 =
            Attestering.Iverksatt(NavIdentBruker.Attestant("Attestant1"), opprettet.plus(3, ChronoUnit.DAYS))

        val actual = Attesteringshistorikk.empty()
            .leggTilNyAttestering(attestering1)
            .leggTilNyAttestering(attestering2)
            .leggTilNyAttestering(attestering3)

        actual shouldBe listOf(attestering1, attestering2, attestering3)
    }

    @Test
    fun `kaster exception dersom man legger til en eldre attestering`() {
        val opprettet = fixedTidspunkt
        val attestering1 = Attestering.Underkjent(
            attestant = NavIdentBruker.Attestant("Attestant2"),
            opprettet = opprettet.plus(1, ChronoUnit.DAYS),
            grunn = UnderkjennAttesteringsgrunnBehandling.BEREGNINGEN_ER_FEIL,
            kommentar = "kommentar",
        )
        val attestering2 = Attestering.Underkjent(
            attestant = NavIdentBruker.Attestant("Attestant2"),
            opprettet = opprettet,
            grunn = UnderkjennAttesteringsgrunnBehandling.BEREGNINGEN_ER_FEIL,
            kommentar = "kommentar",
        )
        shouldThrow<IllegalStateException> {
            Attesteringshistorikk.empty()
                .leggTilNyAttestering(attestering1)
                .leggTilNyAttestering(attestering2)
        }.message shouldBe "Kan ikke legge til en attestering som ikke er nyere enn den forrige attesteringen"
    }

    @Test
    fun `kaster exception dersom man legger til en like gammel attestering`() {
        val opprettet = fixedTidspunkt
        val attestering1 = Attestering.Underkjent(
            attestant = NavIdentBruker.Attestant("Attestant2"),
            opprettet = opprettet,
            grunn = UnderkjennAttesteringsgrunnBehandling.BEREGNINGEN_ER_FEIL,
            kommentar = "kommentar",
        )
        val attestering2 = Attestering.Underkjent(
            attestant = NavIdentBruker.Attestant("Attestant2"),
            opprettet = opprettet,
            grunn = UnderkjennAttesteringsgrunnBehandling.BEREGNINGEN_ER_FEIL,
            kommentar = "kommentar",
        )
        shouldThrow<IllegalStateException> {
            Attesteringshistorikk.empty()
                .leggTilNyAttestering(attestering1)
                .leggTilNyAttestering(attestering2)
        }.message shouldBe "Kan ikke legge til en attestering som ikke er nyere enn den forrige attesteringen"
    }

    @Test
    fun `kan ikke legge til 2 iverksatte attesteringer`() {
        val attestering1 = Attestering.Iverksatt(
            attestant = NavIdentBruker.Attestant(navIdent = "Den første attestanten som iverksatte."),
            opprettet = fixedTidspunkt,
        )
        val attestering2 = Attestering.Iverksatt(
            attestant = NavIdentBruker.Attestant(navIdent = "Den andre attestanten som iverksatte (dette skal feile.)"),
            opprettet = fixedTidspunkt.plus(1, ChronoUnit.MICROS),
        )
        shouldThrow<IllegalStateException> {
            Attesteringshistorikk.empty()
                .leggTilNyAttestering(attestering1)
                .leggTilNyAttestering(attestering2)
        }.message shouldBe "Attesteringshistorikk kan maks inneholde en iverksetting, men var: [Iverksatt(attestant=Den første attestanten som iverksatte., opprettet=2021-01-01T01:02:03.456789Z), Iverksatt(attestant=Den andre attestanten som iverksatte (dette skal feile.), opprettet=2021-01-01T01:02:03.456790Z)]"
    }
}
