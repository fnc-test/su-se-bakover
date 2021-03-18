package no.nav.su.se.bakover.web.routes.revurdering

import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.web.routes.behandling.TestBeregning
import no.nav.su.se.bakover.web.routes.behandling.beregning.toJson
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.vedtak
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class RevurderingJsonTest {

    @Test
    fun `should serialize and deserialize OpprettetRevurdering`() {
        val id = UUID.randomUUID()
        val opprettet = Tidspunkt.now()

        val revurdering = OpprettetRevurdering(
            id = id,
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            opprettet = opprettet,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            grunnlagsdata = Grunnlagsdata.EMPTY,
        )

        val revurderingJson = """
            {
                "id": "$id",
                "opprettet": "$opprettet",
                "tilRevurdering": ${serialize(vedtak.toJson())},
                "status": "${RevurderingsStatus.OPPRETTET}",
                "periode": {
                    "fraOgMed": "2020-01-01",
                    "tilOgMed": "2020-12-31"
                },
                "saksbehandler": "Petter",
                "grunnlag": {
                    "uføre": []
                }
            }
        """.trimIndent()

        JSONAssert.assertEquals(revurderingJson, serialize(revurdering.toJson()), true)
        deserialize<OpprettetRevurderingJson>(revurderingJson) shouldBe revurdering.toJson()
    }

    @Test
    fun `should serialize and deserialize BeregnetInnvilget`() {
        val id = UUID.randomUUID()
        val opprettet = Tidspunkt.now()
        val beregning = TestBeregning

        val revurdering = BeregnetRevurdering.Innvilget(
            id = id,
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            opprettet = opprettet,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            beregning = beregning,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            grunnlagsdata = Grunnlagsdata.EMPTY,
        )

        val revurderingJson = """
            {
                "id": "$id",
                "opprettet": "$opprettet",
                "tilRevurdering": ${serialize(vedtak.toJson())},
                "beregninger":
                  {
                    "beregning": ${serialize(vedtak.beregning.toJson())},
                    "revurdert": ${serialize(beregning.toJson())}
                  },
                "status": "${RevurderingsStatus.BEREGNET_INNVILGET}",
                "saksbehandler": "Petter",
                "periode": {
                    "fraOgMed": "2020-01-01",
                    "tilOgMed": "2020-12-31"
                },
                "grunnlag": {
                    "uføre": []
                }
            }
        """.trimIndent()

        JSONAssert.assertEquals(revurderingJson, serialize(revurdering.toJson()), true)
        deserialize<BeregnetRevurderingJson.Innvilget>(revurderingJson) shouldBe revurdering.toJson()
    }

    @Test
    fun `should serialize and deserialize BeregnetAvslag`() {
        val id = UUID.randomUUID()
        val opprettet = Tidspunkt.now()
        val beregning = TestBeregning

        val revurdering = BeregnetRevurdering.Avslag(
            id = id,
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            opprettet = opprettet,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            beregning = beregning,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            grunnlagsdata = Grunnlagsdata.EMPTY,
        )

        val revurderingJson = """
            {
                "id": "$id",
                "opprettet": "$opprettet",
                "tilRevurdering": ${serialize(vedtak.toJson())},
                "beregninger":
                  {
                    "beregning": ${serialize(vedtak.beregning.toJson())},
                    "revurdert": ${serialize(beregning.toJson())}
                  },
                "status": "${RevurderingsStatus.BEREGNET_AVSLAG}",
                "saksbehandler": "Petter",
                "periode": {
                    "fraOgMed": "2020-01-01",
                    "tilOgMed": "2020-12-31"
                },
                "grunnlag": {
                    "uføre": []
                }                
            }
        """.trimIndent()

        JSONAssert.assertEquals(revurderingJson, serialize(revurdering.toJson()), true)
        deserialize<BeregnetRevurderingJson.Avslag>(revurderingJson) shouldBe revurdering.toJson()
    }

    @Test
    fun `should serialize and deserialize SimulertRevurdering`() {
        val id = UUID.randomUUID()
        val opprettet = Tidspunkt.now()
        val beregning = TestBeregning

        val revurdering = SimulertRevurdering(
            id = id,
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            opprettet = opprettet,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            beregning = beregning,
            simulering = mock(),
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            grunnlagsdata = Grunnlagsdata.EMPTY,
        )

        val revurderingJson = """
            {
                "id": "$id",
                "opprettet": "$opprettet",
                "tilRevurdering": ${serialize(vedtak.toJson())},
                "beregninger":
                  {
                    "beregning": ${serialize(vedtak.beregning.toJson())},
                    "revurdert": ${serialize(beregning.toJson())}
                  },
                "status": "${RevurderingsStatus.SIMULERT}",
                "saksbehandler": "Petter",
                "periode": {
                    "fraOgMed": "2020-01-01",
                    "tilOgMed": "2020-12-31"
                },
                "grunnlag": {
                    "uføre": []
                }                
            }
        """.trimIndent()

        JSONAssert.assertEquals(revurderingJson, serialize(revurdering.toJson()), true)
        deserialize<SimulertRevurderingJson>(revurderingJson) shouldBe revurdering.toJson()
    }

    @Test
    fun `should serialize and deserialize RevurderingTilAttestering`() {
        val id = UUID.randomUUID()
        val opprettet = Tidspunkt.now()
        val beregning = TestBeregning

        val revurdering = RevurderingTilAttestering(
            id = id,
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            opprettet = opprettet,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            beregning = beregning,
            simulering = mock(),
            oppgaveId = OppgaveId("OppgaveId"),
            fritekstTilBrev = "",
            grunnlagsdata = Grunnlagsdata.EMPTY,
        )

        val revurderingJson = """
            {
                "id": "$id",
                "opprettet": "$opprettet",
                "tilRevurdering": ${serialize(vedtak.toJson())},
                "beregninger":
                  {
                    "beregning": ${serialize(vedtak.beregning.toJson())},
                    "revurdert": ${serialize(beregning.toJson())}
                  },
                "status": "${RevurderingsStatus.TIL_ATTESTERING}",
                "saksbehandler": "Petter",
                "periode": {
                    "fraOgMed": "2020-01-01",
                    "tilOgMed": "2020-12-31"
                },
                "grunnlag": {
                    "uføre": []
                }                
            }
        """.trimIndent()

        JSONAssert.assertEquals(revurderingJson, serialize(revurdering.toJson()), true)
        deserialize<TilAttesteringJson>(revurderingJson) shouldBe revurdering.toJson()
    }

    @Test
    fun `should serialize and deserialize UnderkjentRevurdering`() {
        val id = UUID.randomUUID()
        val opprettet = Tidspunkt.now()
        val beregning = TestBeregning

        val revurdering = UnderkjentRevurdering(
            id = id,
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            opprettet = opprettet,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            beregning = beregning,
            simulering = mock(),
            oppgaveId = OppgaveId("OppgaveId"),
            attestering = Attestering.Underkjent(
                attestant = NavIdentBruker.Attestant("attestant"),
                grunn = Attestering.Underkjent.Grunn.DOKUMENTASJON_MANGLER,
                kommentar = "Dokumentasjon mangler"
            ),
            fritekstTilBrev = "",
            grunnlagsdata = Grunnlagsdata.EMPTY,
        )

        val expected = """
            {
                "id": "$id",
                "opprettet": "$opprettet",
                "tilRevurdering": ${serialize(vedtak.toJson())},
                "beregninger":
                  {
                    "beregning": ${serialize(vedtak.beregning.toJson())},
                    "revurdert": ${serialize(beregning.toJson())}
                  },
                "status": "${RevurderingsStatus.UNDERKJENT}",
                "saksbehandler": "Petter",
                "periode": {
                    "fraOgMed": "2020-01-01",
                    "tilOgMed": "2020-12-31"
                },
                "attestering": {
                    "attestant": "attestant",
                    "underkjennelse": {
                        "grunn": "DOKUMENTASJON_MANGLER",
                        "kommentar": "Dokumentasjon mangler"
                    }
                },
                "grunnlag": {
                    "uføre": []
                }                
            }
        """.trimIndent()

        JSONAssert.assertEquals(expected, serialize(revurdering.toJson()), true)
        deserialize<UnderkjentRevurderingJson>(expected) shouldBe revurdering.toJson()
    }

    @Test
    fun `should serialize and deserialize IverksattRevurdering`() {
        val id = UUID.randomUUID()
        val opprettet = Tidspunkt.now()
        val beregning = TestBeregning

        val revurdering = IverksattRevurdering(
            id = id,
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            opprettet = opprettet,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            beregning = beregning,
            simulering = mock(),
            oppgaveId = OppgaveId("OppgaveId"),
            attestering = Attestering.Iverksatt(NavIdentBruker.Attestant("attestant")),
            eksterneIverksettingsteg = mock(),
            utbetalingId = UUID30.randomUUID(),
            fritekstTilBrev = "",
            grunnlagsdata = Grunnlagsdata.EMPTY,
        )

        val revurderingJson = """
            {
                "id": "$id",
                "opprettet": "$opprettet",
                "tilRevurdering": ${serialize(vedtak.toJson())},
                "beregninger":
                  {
                    "beregning": ${serialize(vedtak.beregning.toJson())},
                    "revurdert": ${serialize(beregning.toJson())}
                  },
                "status": "${RevurderingsStatus.IVERKSATT}",
                "saksbehandler": "Petter",
                "periode": {
                    "fraOgMed": "2020-01-01",
                    "tilOgMed": "2020-12-31"
                },
                "attestant": "attestant",
                "grunnlag": {
                    "uføre": []
                }                
            }
        """.trimIndent()

        JSONAssert.assertEquals(revurderingJson, serialize(revurdering.toJson()), true)
        deserialize<IverksattRevurderingJson>(revurderingJson) shouldBe revurdering.toJson()
    }
}
