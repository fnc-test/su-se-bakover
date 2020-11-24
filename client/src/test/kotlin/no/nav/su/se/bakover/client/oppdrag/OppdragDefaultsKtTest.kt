package no.nav.su.se.bakover.client.oppdrag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.endOfDay
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.startOfDay
import org.junit.jupiter.api.Test

internal class OppdragDefaultsKtTest {
    @Test
    fun `format for avstemmingsdato`() {
        val start = 1.januar(2020).startOfDay()
        val end = 1.januar(2020).endOfDay()
        start.toAvstemmingsdatoFormat() shouldBe "2020010100"
        end.toAvstemmingsdatoFormat() shouldBe "2020010123"
    }
}
