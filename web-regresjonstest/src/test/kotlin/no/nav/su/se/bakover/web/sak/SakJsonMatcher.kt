package no.nav.su.se.bakover.web.sak

import no.nav.su.se.bakover.test.jsonAssertEquals
import no.nav.su.se.bakover.web.SharedRegressionTestData

/**
 * Hvis du overskriver en liste, må den wrappes i [].
 */
fun assertSakJson(
    actualSakJson: String,
    expectedId: String = "ignored-by-matcher",
    expectedSaksnummer: Long = 2021,
    expectedFnr: String = SharedRegressionTestData.fnr,
    expectedSøknader: String = "[]",
    expectedBehandlinger: String = "[]",
    expectedUtbetalinger: String = "[]",
    expectedUtbetalingerKanStansesEllerGjenopptas: String = "INGEN",
    expectedRevurderinger: String = "[]",
    expectedVedtak: String = "[]",
    expectedKlager: String = "[]",
    expectedReguleringer: String = "[]",
    expectedSakstype: String = "uføre",
    expectedVedtakPåTidslinje: String = "[]",
    expectedUtenlandsopphold: String = """{"utenlandsopphold": [], "antallDager":  0}""",
    expectedTilbakekrevinger: String = "[]",
    expectedVersjon: Long = 1,
) {
    // language=JSON
    val expectedSakJson = """
    {
        "id": "$expectedId",
        "saksnummer": $expectedSaksnummer,
        "fnr": "$expectedFnr",
        "søknader": $expectedSøknader,
        "behandlinger": $expectedBehandlinger,
        "utbetalinger": $expectedUtbetalinger,
        "utbetalingerKanStansesEllerGjenopptas": "$expectedUtbetalingerKanStansesEllerGjenopptas",
        "revurderinger": $expectedRevurderinger,
        "vedtak": $expectedVedtak,
        "klager": $expectedKlager,
        "reguleringer": $expectedReguleringer,
        "sakstype": $expectedSakstype,
        "vedtakPåTidslinje": $expectedVedtakPåTidslinje,
        "utenlandsopphold": $expectedUtenlandsopphold,
        "versjon": $expectedVersjon,
        "uteståendeAvkorting": null,
        "tilbakekrevinger": $expectedTilbakekrevinger,
        "uteståendeKravgrunnlag": null
    }
    """.trimIndent()
    jsonAssertEquals(
        expected = expectedSakJson,
        actual = actualSakJson,
        "id",
        "søknader[*].id",
        "søknader[*].sakId",
        "utbetalinger[*].sakId",
        "vedtak[*].id",
        "vedtak[*].opprettet",
        "vedtak[*].utbetalingId",
        "vedtak[*].sakId",
        "vedtak[*].behandlingId",
        // Vi lagrer ikke formuegrunnlag i databasen for søknadsbehandlinger. Så denne vil bli generert på nytt hver gang vi gjør en hentSak etc.
        "behandlinger[*].grunnlagsdataOgVilkårsvurderinger.formue.vurderinger[*].id",
        // Vi lagrer ikke formuegrunnlag i databasen for søknadsbehandlinger. Så denne vil bli generert på nytt hver gang vi gjør en hentSak etc.
        "behandlinger[*].grunnlagsdataOgVilkårsvurderinger.formue.vurderinger[*].opprettet",
        "vedtakPåTidslinje[*].vedtakId",
    )
}
