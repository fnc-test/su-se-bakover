package no.nav.su.se.bakover.database

import arrow.core.getOrHandle
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.beregning.TestBeregning
import no.nav.su.se.bakover.database.beregning.toSnapshot
import no.nav.su.se.bakover.database.revurdering.RevurderingPostgresRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingPostgresRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class RevurderingPostgresRepoTest {
    private val ds = EmbeddedDatabase.instance()
    private val søknadsbehandlingRepo: SøknadsbehandlingRepo = SøknadsbehandlingPostgresRepo(ds)
    private val repo: RevurderingPostgresRepo = RevurderingPostgresRepo(ds, søknadsbehandlingRepo)
    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val saksbehandler = Saksbehandler("Sak S. Behandler")
    private val periode = Periode.create(
        fraOgMed = 1.januar(2020),
        tilOgMed = 31.desember(2020),
    )
    private val revurderingsårsak = Revurderingsårsak(
        Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
        Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
    )

    @Test
    fun `kan lagre og hente en revurdering`() {
        withMigratedDb {
            val vedtak =
                testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettet = OpprettetRevurdering(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = fixedTidspunkt,
                tilRevurdering = vedtak,
                saksbehandler = saksbehandler,
                oppgaveId = OppgaveId("oppgaveid"),
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
            )
            repo.lagre(opprettet)
            repo.hent(opprettet.id) shouldBe opprettet

            val beregnetIngenEndring = BeregnetRevurdering.IngenEndring(
                id = opprettet.id,
                periode = periode,
                opprettet = fixedTidspunkt,
                tilRevurdering = vedtak,
                saksbehandler = saksbehandler,
                oppgaveId = OppgaveId("oppgaveid"),
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
                beregning = vedtak.beregning,
            )

            repo.lagre(beregnetIngenEndring)
            repo.hent(opprettet.id) shouldBe beregnetIngenEndring
        }
    }

    @Test
    fun `kan oppdatere revurdering`() {
        withMigratedDb {
            val vedtak =
                testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettetRevurdering = OpprettetRevurdering(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = fixedTidspunkt,
                tilRevurdering = vedtak,
                saksbehandler = saksbehandler,
                oppgaveId = OppgaveId("oppgaveid"),
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
            )
            repo.lagre(opprettetRevurdering)
            val beregnetRevurdering = BeregnetRevurdering.Innvilget(
                id = opprettetRevurdering.id,
                periode = periode,
                opprettet = fixedTidspunkt,
                tilRevurdering = vedtak,
                saksbehandler = saksbehandler,
                beregning = TestBeregning.toSnapshot(),
                oppgaveId = OppgaveId("oppgaveid"),
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
            )

            repo.lagre(beregnetRevurdering)
            repo.hent(beregnetRevurdering.id) shouldBe beregnetRevurdering

            val oppdatertRevurdering = beregnetRevurdering.oppdater(
                Periode.create(1.juni(2020), 30.juni(2020)),
                Revurderingsårsak(
                    årsak = Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                    begrunnelse = Revurderingsårsak.Begrunnelse.create("begrunnelse"),
                ),
            )

            repo.lagre(oppdatertRevurdering)
            val actual = repo.hent(beregnetRevurdering.id)
            actual.shouldBeInstanceOf<OpprettetRevurdering>()
            oppdatertRevurdering.periode shouldBe oppdatertRevurdering.periode
        }
    }

    @Test
    fun `kan kan overskrive en opprettet med innvilget beregning`() {
        withMigratedDb {
            val vedtak =
                testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val opprettet = OpprettetRevurdering(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = fixedTidspunkt,
                tilRevurdering = vedtak,
                saksbehandler = saksbehandler,
                oppgaveId = OppgaveId("oppgaveid"),
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
            )

            repo.lagre(opprettet)

            val beregnetRevurdering = BeregnetRevurdering.Innvilget(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = saksbehandler,
                beregning = TestBeregning,
                oppgaveId = OppgaveId("oppgaveid"),
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
            )

            repo.lagre(beregnetRevurdering)
            assert(repo.hent(opprettet.id) is BeregnetRevurdering.Innvilget)
        }
    }

    @Test
    fun `beregnet kan overskrives med ny avslått beregnet`() {

        withMigratedDb {
            val vedtak =
                testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            val opprettet = OpprettetRevurdering(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = fixedTidspunkt,
                tilRevurdering = vedtak,
                saksbehandler = saksbehandler,
                oppgaveId = OppgaveId("oppgaveid"),
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
            )

            repo.lagre(opprettet)

            val beregnet = BeregnetRevurdering.IngenEndring(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = opprettet.saksbehandler,
                beregning = TestBeregning,
                oppgaveId = OppgaveId("oppgaveid"),
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
            )

            repo.lagre(beregnet)

            val nyBeregnet = BeregnetRevurdering.IngenEndring(
                id = beregnet.id,
                periode = beregnet.periode,
                opprettet = beregnet.opprettet,
                tilRevurdering = beregnet.tilRevurdering,
                saksbehandler = Saksbehandler("ny saksbehandler"),
                beregning = beregnet.beregning,
                oppgaveId = OppgaveId("oppgaveid"),
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
            )

            repo.lagre(nyBeregnet)

            val hentet = repo.hent(opprettet.id)

            hentet shouldNotBe opprettet
            hentet shouldNotBe beregnet
            hentet!!.saksbehandler shouldBe nyBeregnet.saksbehandler
        }
    }

    @Test
    fun `kan overskrive en beregnet med simulert`() {
        withMigratedDb {
            val vedtak =
                testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            val opprettet = OpprettetRevurdering(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = fixedTidspunkt,
                tilRevurdering = vedtak,
                saksbehandler = saksbehandler,
                oppgaveId = OppgaveId("oppgaveid"),
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
            )

            repo.lagre(opprettet)

            val beregnet = BeregnetRevurdering.Innvilget(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = opprettet.saksbehandler,
                beregning = TestBeregning,
                oppgaveId = OppgaveId("oppgaveid"),
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
            )

            repo.lagre(beregnet)

            val simulert = SimulertRevurdering.Innvilget(
                id = beregnet.id,
                periode = beregnet.periode,
                opprettet = beregnet.opprettet,
                tilRevurdering = beregnet.tilRevurdering,
                saksbehandler = beregnet.saksbehandler,
                beregning = beregnet.beregning,
                oppgaveId = OppgaveId("oppgaveid"),
                simulering = Simulering(
                    gjelderId = FnrGenerator.random(),
                    gjelderNavn = "et navn for simulering",
                    datoBeregnet = 1.januar(2021),
                    nettoBeløp = 200,
                    periodeList = listOf(),
                ),
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
            )

            repo.lagre(simulert)

            val hentet = repo.hent(opprettet.id)

            assert(hentet is SimulertRevurdering.Innvilget)
        }
    }

    @Test
    fun `kan overskrive en simulert med en beregnet`() {
        withMigratedDb {
            val vedtak =
                testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            val opprettet = OpprettetRevurdering(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = fixedTidspunkt,
                tilRevurdering = vedtak,
                saksbehandler = saksbehandler,
                oppgaveId = OppgaveId("oppgaveid"),
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
            )

            repo.lagre(opprettet)

            val beregnet = BeregnetRevurdering.Innvilget(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = opprettet.saksbehandler,
                beregning = TestBeregning,
                oppgaveId = OppgaveId("oppgaveid"),
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
            )

            repo.lagre(beregnet)

            val simulert = SimulertRevurdering.Innvilget(
                id = beregnet.id,
                periode = beregnet.periode,
                opprettet = beregnet.opprettet,
                tilRevurdering = beregnet.tilRevurdering,
                saksbehandler = beregnet.saksbehandler,
                beregning = beregnet.beregning,
                oppgaveId = OppgaveId("oppgaveid"),
                simulering = Simulering(
                    gjelderId = FnrGenerator.random(),
                    gjelderNavn = "et navn for simulering",
                    datoBeregnet = 1.januar(2021),
                    nettoBeløp = 200,
                    periodeList = listOf(),
                ),
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
            )

            repo.lagre(simulert)
            repo.lagre(beregnet)
            val hentet = repo.hent(opprettet.id)

            assert(hentet is BeregnetRevurdering.Innvilget)
        }
    }

    @Test
    fun `kan overskrive en simulert med en til attestering`() {
        withMigratedDb {
            val vedtak =
                testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            val opprettet = OpprettetRevurdering(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = fixedTidspunkt,
                tilRevurdering = vedtak,
                saksbehandler = saksbehandler,
                oppgaveId = OppgaveId("oppgaveid"),
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
            )

            repo.lagre(opprettet)

            val beregnet = BeregnetRevurdering.Innvilget(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = opprettet.saksbehandler,
                beregning = TestBeregning,
                oppgaveId = OppgaveId("oppgaveid"),
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
            )

            repo.lagre(beregnet)

            val simulert = SimulertRevurdering.Innvilget(
                id = beregnet.id,
                periode = beregnet.periode,
                opprettet = beregnet.opprettet,
                tilRevurdering = beregnet.tilRevurdering,
                saksbehandler = beregnet.saksbehandler,
                beregning = beregnet.beregning,
                oppgaveId = OppgaveId("oppgaveid"),
                simulering = Simulering(
                    gjelderId = FnrGenerator.random(),
                    gjelderNavn = "et navn for simulering",
                    datoBeregnet = 1.januar(2021),
                    nettoBeløp = 200,
                    periodeList = listOf(),
                ),
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
            )

            repo.lagre(simulert)

            val tilAttestering =
                simulert.tilAttestering(
                    attesteringsoppgaveId = OppgaveId("attesteringsoppgaveId"),
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = "fritekst",
                )

            repo.lagre(tilAttestering)

            val hentet = repo.hent(opprettet.id)

            assert(hentet is RevurderingTilAttestering.Innvilget)
        }
    }

    @Test
    fun `saksbehandler som sender til attestering overskriver saksbehandlere som var før`() {
        withMigratedDb {
            val vedtak =
                testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            val opprettet = OpprettetRevurdering(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = fixedTidspunkt,
                tilRevurdering = vedtak,
                saksbehandler = saksbehandler,
                oppgaveId = OppgaveId("oppgaveid"),
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
            )

            repo.lagre(opprettet)

            val beregnet = BeregnetRevurdering.Innvilget(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = opprettet.saksbehandler,
                beregning = TestBeregning,
                oppgaveId = OppgaveId("oppgaveid"),
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
            )

            repo.lagre(beregnet)

            val simulert = SimulertRevurdering.Innvilget(
                id = beregnet.id,
                periode = beregnet.periode,
                opprettet = beregnet.opprettet,
                tilRevurdering = beregnet.tilRevurdering,
                saksbehandler = beregnet.saksbehandler,
                beregning = beregnet.beregning,
                oppgaveId = OppgaveId("oppgaveid"),
                simulering = Simulering(
                    gjelderId = FnrGenerator.random(),
                    gjelderNavn = "et navn for simulering",
                    datoBeregnet = 1.januar(2021),
                    nettoBeløp = 200,
                    periodeList = listOf(),
                ),
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
            )

            repo.lagre(simulert)

            val tilAttestering = simulert.tilAttestering(
                attesteringsoppgaveId = OppgaveId("attesteringsoppgaveId"),
                saksbehandler = Saksbehandler("Ny saksbehandler"),
                fritekstTilBrev = "fritekst",
            )

            repo.lagre(tilAttestering)

            val hentet = repo.hent(opprettet.id)

            assert(hentet is RevurderingTilAttestering.Innvilget)
            hentet!!.saksbehandler shouldNotBe opprettet.saksbehandler
        }
    }

    @Test
    fun `kan lagre og hente en iverksatt revurdering`() {
        withMigratedDb {
            val vedtak =
                testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            val attestant = NavIdentBruker.Attestant("Attestansson")

            val opprettet = OpprettetRevurdering(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = fixedTidspunkt,
                tilRevurdering = vedtak,
                saksbehandler = saksbehandler,
                oppgaveId = OppgaveId("oppgaveid"),
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
            )
            repo.lagre(opprettet)

            val tilAttestering = RevurderingTilAttestering.Innvilget(
                id = opprettet.id,
                periode = periode,
                opprettet = fixedTidspunkt,
                tilRevurdering = vedtak,
                saksbehandler = saksbehandler,
                beregning = TestBeregning.toSnapshot(),
                simulering = Simulering(
                    gjelderId = FnrGenerator.random(),
                    gjelderNavn = "Navn Navnesson",
                    datoBeregnet = LocalDate.now(),
                    nettoBeløp = 5,
                    periodeList = listOf(),
                ),
                oppgaveId = OppgaveId(value = ""),
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
            )
            val utbetaling = testDataHelper.nyUtbetalingUtenKvittering(
                revurderingTilAttestering = tilAttestering,
            )

            val iverksatt = tilAttestering.tilIverksatt(
                attestant = attestant,
                utbetal = { utbetaling.id.right() },
            ).getOrHandle { throw RuntimeException("Skal ikke kunne skje") }

            repo.lagre(iverksatt)
            repo.hent(iverksatt.id) shouldBe iverksatt
            ds.withSession {
                repo.hentRevurderingerForSak(iverksatt.sakId, it) shouldBe listOf(iverksatt)
            }
        }
    }

    @Test
    fun `kan lagre og hente en underkjent revurdering`() {
        withMigratedDb {
            val vedtak = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first
            val opprettet = OpprettetRevurdering(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = fixedTidspunkt,
                tilRevurdering = vedtak,
                saksbehandler = saksbehandler,
                oppgaveId = OppgaveId("oppgaveid"),
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
            )

            repo.lagre(opprettet)

            val beregnet = BeregnetRevurdering.Innvilget(
                id = opprettet.id,
                periode = opprettet.periode,
                opprettet = opprettet.opprettet,
                tilRevurdering = vedtak,
                saksbehandler = opprettet.saksbehandler,
                beregning = TestBeregning,
                oppgaveId = OppgaveId("oppgaveid"),
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
            )

            repo.lagre(beregnet)

            val simulert = SimulertRevurdering.Innvilget(
                id = beregnet.id,
                periode = beregnet.periode,
                opprettet = beregnet.opprettet,
                tilRevurdering = beregnet.tilRevurdering,
                saksbehandler = beregnet.saksbehandler,
                beregning = beregnet.beregning,
                oppgaveId = OppgaveId("oppgaveid"),
                simulering = Simulering(
                    gjelderId = FnrGenerator.random(),
                    gjelderNavn = "et navn for simulering",
                    datoBeregnet = 1.januar(2021),
                    nettoBeløp = 200,
                    periodeList = listOf(),
                ),
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
            )

            repo.lagre(simulert)

            val tilAttestering =
                simulert.tilAttestering(
                    attesteringsoppgaveId = OppgaveId("attesteringsoppgaveId"),
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = "",
                )

            val attestering = Attestering.Underkjent(
                attestant = NavIdentBruker.Attestant(navIdent = "123"),
                grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                kommentar = "feil",
            )

            repo.lagre(tilAttestering.underkjenn(attestering, OppgaveId("nyOppgaveId")))

            assert(repo.hent(opprettet.id) is UnderkjentRevurdering.Innvilget)
            repo.hentEventuellTidligereAttestering(opprettet.id) shouldBe attestering
        }
    }
}
