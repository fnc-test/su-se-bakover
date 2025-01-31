package no.nav.su.se.bakover.web.avkorting

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørAvkorting
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.revurdering.opprettIverksattRevurdering
import no.nav.su.se.bakover.web.revurdering.utenlandsopphold.leggTilUtenlandsoppholdRevurdering
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.SKIP_STEP
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.json.JSONObject
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Spesialtilfelle der vi ikke sender utbetaling.
 * Motstykket til [DelerAvRevurderingsperiodenFørerTilAvkortingIT]
 */
internal class HeleRevurderingsperiodenFørerTilAvkortingIT {
    @Test
    fun `hele revurderingsperioden fører til avkorting`() {
        // Vi starter 1 januar 2022 for å emulere at alle månedene er utbetalt (etterbetaling) og revurderingen sin simulering fører til feilutbetalinger.
        val tikkendeKlokke = TikkendeKlokke(fixedClockAt(1.januar(2022)))
        SharedRegressionTestData.withTestApplicationAndEmbeddedDb(
            clock = tikkendeKlokke,
        ) { appComponents ->
            val stønadStart = 1.januar(2021)
            val stønadSlutt = 31.desember(2021)
            val fnr = Fnr.generer().toString()
            opprettInnvilgetSøknadsbehandling(
                fnr = fnr,
                fraOgMed = stønadStart.toString(),
                tilOgMed = stønadSlutt.toString(),
                client = this.client,
                appComponents = appComponents,
            ).let { søknadsbehandlingJson ->
                val sakId = BehandlingJson.hentSakId(søknadsbehandlingJson)

                opprettIverksattRevurdering(
                    sakid = sakId,
                    fraogmed = stønadStart.toString(),
                    tilogmed = stønadSlutt.toString(),
                    client = this.client,
                    appComponents = appComponents,

                    leggTilUføregrunnlag = { _, _, _, _, _, _, _ -> SKIP_STEP },
                    leggTilBosituasjon = { _, _, _, _ -> SKIP_STEP },
                    leggTilFormue = { _, _, _, _ -> SKIP_STEP },
                    informasjonSomRevurderes = "[\"Utenlandsopphold\"]",
                    leggTilUtenlandsoppholdRevurdering = { _, behandlingId, _, _, _ ->
                        leggTilUtenlandsoppholdRevurdering(
                            sakId = sakId,
                            behandlingId = behandlingId,
                            fraOgMed = stønadStart.toString(),
                            tilOgMed = stønadSlutt.toString(),
                            client = client,
                            vurdering = "SkalVæreMerEnn90DagerIUtlandet",
                        )
                    },
                    leggTilFlyktningVilkår = { _, _, _, _, _, _ -> SKIP_STEP },
                    leggTilFradrag = { _, _, _, _ -> SKIP_STEP },
                ).also {
                    JSONObject(it).let {
                        it.get("tilbakekrevingsbehandling") shouldBe null
                        it.getJSONObject("simuleringForAvkortingsvarsel").getJSONObject("totalOppsummering").toString() shouldEqualJson """
                        {
                          "fraOgMed": "2021-01-01",
                          "tilOgMed": "2021-12-31",
                          "sumTilUtbetaling": 0,
                          "sumEtterbetaling": 0,
                          "sumFramtidigUtbetaling": 0,
                          "sumTotalUtbetaling": 0,
                          "sumTidligereUtbetalt": 259696,
                          "sumFeilutbetaling": 259696,
                          "sumReduksjonFeilkonto": 0
                        }
                        """.trimIndent()
                    }
                    appComponents.databaseRepos.sak.hentSak(UUID.fromString(sakId))!!.let {
                        it.utbetalinger.size shouldBe 1
                        it.vedtakListe.size shouldBe 2
                        it.vedtakListe[1] shouldBe beOfType<VedtakOpphørAvkorting>()
                        it.uteståendeAvkorting shouldBe beOfType<Avkortingsvarsel.Utenlandsopphold.SkalAvkortes>()
                    }
                }
            }
        }
    }
}
