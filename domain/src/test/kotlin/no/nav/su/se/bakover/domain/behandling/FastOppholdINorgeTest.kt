package no.nav.su.se.bakover.domain.behandling

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class FastOppholdINorgeTest {

    @Test
    fun `er ikke ferdigbehandlet hvis status er uavklart`() {
        Behandlingsinformasjon.FastOppholdINorge(
            status = Behandlingsinformasjon.FastOppholdINorge.Status.Uavklart,
            begrunnelse = "neh"
        ).let {
            it.erVilkårOppfylt() shouldBe false
            it.erVilkårIkkeOppfylt() shouldBe false
        }
    }

    @Test
    fun `er oppfylt hvis status er oppfylt`() {
        Behandlingsinformasjon.FastOppholdINorge(
            status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårOppfylt,
            begrunnelse = "neh"
        ).erVilkårOppfylt() shouldBe true
    }

    @Test
    fun `er ikke oppfylt hvis status er ikke oppfylt`() {
        Behandlingsinformasjon.FastOppholdINorge(
            status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårIkkeOppfylt,
            begrunnelse = "neh"
        ).erVilkårOppfylt() shouldBe false
    }
}
