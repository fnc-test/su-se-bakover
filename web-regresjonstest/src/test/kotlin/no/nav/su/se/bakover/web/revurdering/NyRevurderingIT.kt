package no.nav.su.se.bakover.web.revurdering

import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.sak.assertSakJson
import no.nav.su.se.bakover.web.sak.hent.hentSak
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.json.JSONObject
import org.junit.jupiter.api.Test

internal class NyRevurderingIT {
    @Test
    fun `revurdering av eksisterende søknadsbehandling`() {
        SharedRegressionTestData.withTestApplicationAndEmbeddedDb(
            clock = TikkendeKlokke(fixedClock),
        ) {
            val stønadStart = fixedLocalDate.startOfMonth()
            val stønadSlutt = fixedLocalDate.plusMonths(11).endOfMonth()
            val fnr = Fnr.generer().toString()

            opprettInnvilgetSøknadsbehandling(
                fnr = fnr,
                fraOgMed = stønadStart.toString(),
                tilOgMed = stønadSlutt.toString(),
            ).let { søknadsbehandlingJson ->

                val sakId = BehandlingJson.hentSakId(søknadsbehandlingJson)

                opprettIverksattRevurdering(
                    sakId = sakId,
                    fraOgMed = stønadStart.plusMonths(4).startOfMonth().toString(),
                    tilOgMed = stønadSlutt.toString(),
                ).let { revurderingJson ->
                    hentSak(sakId).also {
                        assertSakJson(
                            actualSakJson = it,
                            expectedFnr = fnr,
                            expectedId = sakId,
                            expectedUtbetalingerKanStansesEllerGjenopptas = "STANS",
                            expectedBehandlinger = "[$søknadsbehandlingJson]",
                            expectedUtbetalinger = """
                                [
                                    {
                                        "beløp":20946,
                                        "fraOgMed": "2021-01-01",
                                        "tilOgMed": "2021-04-30",
                                        "type": "NY"
                                    },
                                    {
                                        "beløp":10482,
                                        "fraOgMed": "2021-05-01",
                                        "tilOgMed": "2021-12-31",
                                        "type": "NY"
                                    }
                                ]
                            """.trimIndent(),
                            expectedSøknader = """
                                [
                                    ${JSONObject(søknadsbehandlingJson).getJSONObject("søknad")}
                                ]
                            """.trimIndent(),
                            expectedRevurderinger = "[$revurderingJson]",
                            expectedVedtak = """
                                [
                                    {
                                        "id":"ignore-me",
                                        "opprettet":"ignore-me",
                                        "beregning":${JSONObject(søknadsbehandlingJson).getJSONObject("beregning")},
                                        "simulering":${JSONObject(søknadsbehandlingJson).getJSONObject("simulering")},
                                        "attestant":"automatiskAttesteringAvSøknadsbehandling",
                                        "saksbehandler":"Z990Lokal",
                                        "utbetalingId":"ignore-me",
                                        "behandlingId":"ignore-me",
                                        "sakId":"ignore-me",
                                        "saksnummer":"2021",
                                        "fnr":"$fnr",
                                        "periode":{
                                          "fraOgMed":"2021-01-01",
                                          "tilOgMed":"2021-12-31"
                                        },
                                        "type":"SØKNAD"
                                    },
                                    {
                                        "id":"ignore-me",
                                        "opprettet":"ignore-me",
                                        "beregning":${JSONObject(revurderingJson).getJSONObject("beregning")},
                                        "simulering":${JSONObject(revurderingJson).getJSONObject("simulering")},
                                        "attestant":"automatiskAttesteringAvSøknadsbehandling",
                                        "saksbehandler":"Z990Lokal",
                                        "utbetalingId":"ignore-me",
                                        "behandlingId":"ignore-me",
                                        "sakId":"ignore-me",
                                        "saksnummer":"2021",
                                        "fnr":"$fnr",
                                        "periode":{
                                          "fraOgMed":"2021-05-01",
                                          "tilOgMed":"2021-12-31"
                                        },
                                        "type":"ENDRING"
                                    }
                            ]
                            """.trimIndent(),
                        )
                    }
                }
            }
        }
    }
}
