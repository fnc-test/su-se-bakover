package no.nav.su.se.bakover.web.routes.grunnlag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.test.grunnlagsdataEnsligMedFradrag
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragJson.Companion.toJson
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

class FradragsgrunnlagJsonTest {

    @Test
    fun `serialiserer og deserialiserer fradragsgrunnlag`() {
        JSONAssert.assertEquals(expectedFradragsgrunnlagJson, serialize(fradragsgrunnlag.toJson()), true)
        deserialize<FradragJson>(expectedFradragsgrunnlagJson) shouldBe fradragsgrunnlag.toJson()
    }

    @Test
    fun `serialiserer og deserialiserer fradragsgrunnlag med annet`() {
        val fradrag = FradragFactory.ny(
            type = Fradragstype.from(Fradragstype.Kategori.Annet, "vant på flaxlodd"),
            månedsbeløp = 1000.0,
            periode = periode2021,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        )
        val exp = """
            {
              "periode" : {
                "fraOgMed" : "2021-01-01",
                "tilOgMed" : "2021-12-31"
              },
              "type" : "Annet",
              "beskrivelse" : "vant på flaxlodd",
              "beløp": 1000.0,
              "utenlandskInntekt": null,
              "tilhører": "BRUKER"
            }
        """
        JSONAssert.assertEquals(exp, serialize(fradrag.toJson()), true)
        deserialize<FradragJson>(exp) shouldBe fradrag.toJson()
    }

    companion object {
        //language=JSON
        internal val expectedFradragsgrunnlagJson = """
            {
              "periode" : {
                "fraOgMed" : "2021-01-01",
                "tilOgMed" : "2021-12-31"
              },
              "type" : "Arbeidsinntekt",
              "beskrivelse" :null,
              "beløp": 1000.0,
              "utenlandskInntekt": null,
              "tilhører": "BRUKER"
            }
        """.trimIndent()

        internal val fradragsgrunnlag = grunnlagsdataEnsligMedFradrag().fradragsgrunnlag.first()
    }
}
