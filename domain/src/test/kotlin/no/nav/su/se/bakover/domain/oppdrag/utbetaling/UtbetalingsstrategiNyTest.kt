package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling.Companion.hentOversendteUtbetalingerUtenFeil
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.LocalDate
import java.time.Month
import java.util.UUID

internal class UtbetalingsstrategiNyTest {
    private val sakId = UUID.randomUUID()
    private val saksnummer = Saksnummer(2021)

    private val fnr = Fnr("12345678910")

    private object BeregningMedTomMånedsbereninger : Beregning {
        override fun getId(): UUID = mock()
        override fun getOpprettet(): Tidspunkt = mock()
        override fun getSats(): Sats = Sats.HØY
        override fun getMånedsberegninger(): List<Månedsberegning> = emptyList()
        override fun getFradrag(): List<Fradrag> = emptyList()
        override fun getSumYtelse(): Int = 1000
        override fun getSumFradrag(): Double = 1000.0
        override fun getBegrunnelse(): String = mock()
        override fun equals(other: Any?): Boolean = mock()
        override val periode: Periode = Periode.create(1.juni(2021), 30.november(2021))
    }

    @Test
    fun `ingen eksisterende utbetalinger`() {
        val uføregrunnlagListe = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.januar(2020), tilOgMed = 30.april(2020)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
        )
        val actual = Utbetalingsstrategi.Ny(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            beregning = createBeregning(1.januar(2020), 30.april(2020)),
            utbetalinger = listOf(),
            clock = fixedClock,
            uføregrunnlag = uføregrunnlagListe,
        ).generate()

        val first = actual.utbetalingslinjer.first()
        actual shouldBe expectedUtbetaling(
            actual,
            nonEmptyListOf(
                expectedUtbetalingslinje(
                    utbetalingslinjeId = first.id,
                    opprettet = first.opprettet,
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 30.april(2020),
                    beløp = 20637,
                    forrigeUtbetalingslinjeId = null,
                ),
            ),
        )
    }

    @Test
    fun `nye utbetalingslinjer skal refere til forutgående utbetalingslinjer`() {
        val forrigeUtbetalingslinjeId = UUID30.randomUUID()

        val uføregrunnlagListe = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.januar(2020), tilOgMed = 31.desember(2020)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
        )

        val eksisterendeUtbetalinger = listOf(
            Utbetaling.OversendtUtbetaling.MedKvittering(
                opprettet = fixedTidspunkt,
                sakId = sakId,
                saksnummer = saksnummer,
                simulering = Simulering(
                    gjelderId = fnr,
                    gjelderNavn = "navn",
                    datoBeregnet = idag(fixedClock),
                    nettoBeløp = 0,
                    periodeList = listOf(),
                ),
                kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK, "", mottattTidspunkt = fixedTidspunkt),
                utbetalingsrequest = Utbetalingsrequest(
                    value = "",
                ),
                utbetalingslinjer = nonEmptyListOf(
                    Utbetalingslinje.Ny(
                        id = forrigeUtbetalingslinjeId,
                        opprettet = Tidspunkt.MIN,
                        fraOgMed = 1.januar(2018),
                        tilOgMed = 31.desember(2018),
                        forrigeUtbetalingslinjeId = null,
                        beløp = 5000,
                        uføregrad = Uføregrad.parse(50),
                    ),
                ),
                fnr = fnr,
                type = Utbetaling.UtbetalingsType.NY,
                behandler = NavIdentBruker.Saksbehandler("Z123"),
            ),
        )

        val nyUtbetaling = Utbetalingsstrategi.Ny(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalinger = eksisterendeUtbetalinger,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            beregning = createBeregning(
                fraOgMed = 1.januar(2020),
                tilOgMed = 31.desember(2020),
            ),
            clock = fixedClock,
            uføregrunnlagListe,
        ).generate()

        nyUtbetaling shouldBe Utbetaling.UtbetalingForSimulering(
            id = nyUtbetaling.id,
            opprettet = nyUtbetaling.opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            utbetalingslinjer = nonEmptyListOf(
                expectedUtbetalingslinje(
                    utbetalingslinjeId = nyUtbetaling.utbetalingslinjer[0].id,
                    opprettet = nyUtbetaling.utbetalingslinjer[0].opprettet,
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 30.april(2020),
                    beløp = 20637,
                    forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
                ),
                expectedUtbetalingslinje(
                    utbetalingslinjeId = nyUtbetaling.utbetalingslinjer[1].id,
                    opprettet = nyUtbetaling.utbetalingslinjer[1].opprettet,
                    fraOgMed = 1.mai(2020),
                    tilOgMed = 31.desember(2020),
                    beløp = 20946,
                    forrigeUtbetalingslinjeId = nyUtbetaling.utbetalingslinjer[0].id,
                ),
            ),
            fnr = fnr,
            type = Utbetaling.UtbetalingsType.NY,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            avstemmingsnøkkel = Avstemmingsnøkkel(fixedTidspunkt),
        )
    }

    @Test
    fun `tar utgangspunkt i nyeste utbetalte ved opprettelse av nye utbetalinger`() {
        val dummyUtbetalingslinjer = nonEmptyListOf(
            Utbetalingslinje.Ny(
                id = UUID30.randomUUID(),
                opprettet = fixedTidspunkt,
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.januar(2021),
                forrigeUtbetalingslinjeId = null,
                beløp = 0,
                uføregrad = Uføregrad.parse(50),
            ),
        )

        val first = Utbetaling.OversendtUtbetaling.MedKvittering(
            id = UUID30.randomUUID(),
            opprettet = LocalDate.of(2020, Month.JANUARY, 1).startOfDay(),
            sakId = sakId,
            saksnummer = saksnummer,
            utbetalingsrequest = Utbetalingsrequest(""),
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK, "", mottattTidspunkt = fixedTidspunkt),
            utbetalingslinjer = dummyUtbetalingslinjer,
            fnr = fnr,
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "navn",
                datoBeregnet = idag(fixedClock),
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
            type = Utbetaling.UtbetalingsType.NY,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
        )

        val second = Utbetaling.OversendtUtbetaling.MedKvittering(
            id = UUID30.randomUUID(),
            opprettet = LocalDate.of(2020, Month.FEBRUARY, 1).startOfDay(),
            sakId = sakId,
            saksnummer = saksnummer,
            utbetalingsrequest = Utbetalingsrequest(""),
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.FEIL, "", mottattTidspunkt = fixedTidspunkt),
            utbetalingslinjer = dummyUtbetalingslinjer,
            fnr = fnr,
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "navn",
                datoBeregnet = idag(fixedClock),
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
            type = Utbetaling.UtbetalingsType.NY,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
        )

        val third = Utbetaling.OversendtUtbetaling.MedKvittering(
            id = UUID30.randomUUID(),
            opprettet = LocalDate.of(2020, Month.MARCH, 1).startOfDay(),
            sakId = sakId,
            saksnummer = saksnummer,
            utbetalingsrequest = Utbetalingsrequest(""),
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK_MED_VARSEL, "", mottattTidspunkt = fixedTidspunkt),
            utbetalingslinjer = dummyUtbetalingslinjer,
            fnr = fnr,
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "navn",
                datoBeregnet = idag(fixedClock),
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
            type = Utbetaling.UtbetalingsType.NY,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
        )
        val fourth = Utbetaling.OversendtUtbetaling.MedKvittering(
            id = UUID30.randomUUID(),
            opprettet = LocalDate.of(2020, Month.JULY, 1).startOfDay(),
            sakId = sakId,
            saksnummer = saksnummer,
            utbetalingsrequest = Utbetalingsrequest(""),
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.FEIL, "", mottattTidspunkt = fixedTidspunkt),
            utbetalingslinjer = dummyUtbetalingslinjer,
            fnr = fnr,
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "navn",
                datoBeregnet = idag(fixedClock),
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
            type = Utbetaling.UtbetalingsType.NY,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
        )
        val utbetalinger = listOf(first, second, third, fourth)
        utbetalinger.hentOversendteUtbetalingerUtenFeil()[1] shouldBe third
    }

    @Test
    fun `konverterer tilstøtende beregningsperioder med forskjellig beløp til separate utbetalingsperioder`() {
        val uføregrunnlagListe = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.januar(2020), tilOgMed = 31.desember(2020)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
        )
        val actualUtbetaling = Utbetalingsstrategi.Ny(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalinger = emptyList(),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            beregning = createBeregning(fraOgMed = 1.januar(2020), tilOgMed = 31.desember(2020)),
            clock = fixedClock,
            uføregrunnlagListe,
        ).generate()
        actualUtbetaling shouldBe Utbetaling.UtbetalingForSimulering(
            id = actualUtbetaling.id,
            opprettet = actualUtbetaling.opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Ny(
                    id = actualUtbetaling.utbetalingslinjer[0].id,
                    opprettet = actualUtbetaling.utbetalingslinjer[0].opprettet,
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 30.april(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 20637,
                    uføregrad = Uføregrad.parse(50),
                ),
                Utbetalingslinje.Ny(
                    id = actualUtbetaling.utbetalingslinjer[1].id,
                    opprettet = actualUtbetaling.utbetalingslinjer[1].opprettet,
                    fraOgMed = 1.mai(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = actualUtbetaling.utbetalingslinjer[0].id,
                    beløp = 20946,
                    uføregrad = Uføregrad.parse(50),
                ),
            ),
            fnr = fnr,
            type = Utbetaling.UtbetalingsType.NY,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            avstemmingsnøkkel = Avstemmingsnøkkel(fixedTidspunkt),
        )
    }

    @Test
    fun `perioder som har likt beløp, men ikke tilstøter hverandre får separate utbetalingsperioder`() {
        val uføregrunnlagListe = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.januar(2020), tilOgMed = 30.april(2020)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
        )
        val actualUtbetaling = Utbetalingsstrategi.Ny(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalinger = emptyList(),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            beregning = BeregningFactory(clock = fixedClock).ny(
                fradrag = listOf(
                    FradragFactory.ny(
                        type = Fradragstype.ForventetInntekt,
                        månedsbeløp = 1000.0,
                        periode = Periode.create(fraOgMed = 1.januar(2020), tilOgMed = 30.april(2020)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 4000.0,
                        periode = Periode.create(fraOgMed = 1.februar(2020), tilOgMed = 29.februar(2020)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
                beregningsperioder = listOf(
                    Beregningsperiode(
                        periode = Periode.create(1.januar(2020), 30.april(2020)),
                        strategy = BeregningStrategy.BorAlene,
                    )
                )
            ),
            clock = fixedClock,
            uføregrunnlagListe,
        ).generate()
        actualUtbetaling shouldBe Utbetaling.UtbetalingForSimulering(
            id = actualUtbetaling.id,
            opprettet = actualUtbetaling.opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Ny(
                    id = actualUtbetaling.utbetalingslinjer[0].id,
                    opprettet = actualUtbetaling.utbetalingslinjer[0].opprettet,
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.januar(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 19637,
                    uføregrad = Uføregrad.parse(50),
                ),
                Utbetalingslinje.Ny(
                    id = actualUtbetaling.utbetalingslinjer[1].id,
                    opprettet = actualUtbetaling.utbetalingslinjer[1].opprettet,
                    fraOgMed = 1.februar(2020),
                    tilOgMed = 29.februar(2020),
                    forrigeUtbetalingslinjeId = actualUtbetaling.utbetalingslinjer[0].id,
                    beløp = 16637,
                    uføregrad = Uføregrad.parse(50),
                ),
                Utbetalingslinje.Ny(
                    id = actualUtbetaling.utbetalingslinjer[2].id,
                    opprettet = actualUtbetaling.utbetalingslinjer[2].opprettet,
                    fraOgMed = 1.mars(2020),
                    tilOgMed = 30.april(2020),
                    forrigeUtbetalingslinjeId = actualUtbetaling.utbetalingslinjer[1].id,
                    beløp = actualUtbetaling.utbetalingslinjer[0].beløp,
                    uføregrad = Uføregrad.parse(50),
                ),
            ),
            fnr = fnr,
            type = Utbetaling.UtbetalingsType.NY,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            avstemmingsnøkkel = Avstemmingsnøkkel(fixedTidspunkt),
        )
    }

    @Test
    fun `kaster exception hvis månedsberegning og uføregrunnlag er tomme (0 til 0)`() {
        val uføreList = listOf<Grunnlag.Uføregrunnlag>()

        shouldThrow<RuntimeException> {
            Utbetalingsstrategi.Ny(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalinger = listOf(),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                clock = fixedClock,
                beregning = BeregningMedTomMånedsbereninger,
                uføregrunnlag = uføreList,
            ).generate()
        }
    }

    @Test
    fun `kaster exception hvis månedsberegning er tom, men finnes uføregrunnlag (0 til 1)`() {
        val uføreList = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
        )

        shouldThrow<RuntimeException> {
            Utbetalingsstrategi.Ny(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalinger = listOf(),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                clock = fixedClock,
                beregning = BeregningMedTomMånedsbereninger,
                uføregrunnlag = uføreList,
            ).generate()
        }
    }

    @Test
    fun `kaster exception hvis månedsberegning er tom, men finnes flere uføregrunnlag (0 til mange)`() {
        val uføreList = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.mai(2021)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.juni(2021), tilOgMed = 31.desember(2021)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
        )

        shouldThrow<RuntimeException> {
            Utbetalingsstrategi.Ny(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalinger = listOf(),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                clock = fixedClock,
                beregning = BeregningMedTomMånedsbereninger,
                uføregrunnlag = uføreList,
            ).generate()
        }
    }

    @Test
    fun `kaster exception hvis det finnes månedsberegning, men uføregrunnlag er tom (1 til 0)`() {
        shouldThrow<Utbetalingsstrategi.UtbetalingStrategyException> {
            Utbetalingsstrategi.Ny(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalinger = listOf(),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                clock = fixedClock,
                beregning = createBeregning(1.januar(2021), 31.desember(2021)),
                uføregrunnlag = emptyList(),
            ).generate()
        }
    }

    @Test
    fun `kaster exception hvis det finnes flere månedsberegninger, men uføregrunnlag er tom (mange til 0)`() {
        val periode = Periode.create(1.januar(2021), 31.desember(2021))
        shouldThrow<Utbetalingsstrategi.UtbetalingStrategyException> {
            Utbetalingsstrategi.Ny(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalinger = listOf(),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                clock = fixedClock,
                beregning = BeregningFactory(clock = fixedClock).ny(
                    fradrag = listOf(
                        FradragFactory.ny(
                            type = Fradragstype.ForventetInntekt,
                            månedsbeløp = 0.0,
                            periode = periode,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                        FradragFactory.ny(
                            type = Fradragstype.Sosialstønad,
                            månedsbeløp = 1000.0,
                            periode = Periode.create(1.januar(2021), 31.mai(2021)),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                        FradragFactory.ny(
                            type = Fradragstype.Kontantstøtte,
                            månedsbeløp = 3000.0,
                            periode = Periode.create(1.juni(2021), 31.desember(2021)),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    beregningsperioder = listOf(
                        Beregningsperiode(
                            periode = periode,
                            strategy = BeregningStrategy.BorAlene,
                        )
                    )
                ),
                uføregrunnlag = emptyList(),
            ).generate()
        }
    }

    @Test
    fun `legger på uføregrad på utbetalingslinjer (1 til 1)`() {
        val uføreList = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
        )

        val actual = Utbetalingsstrategi.Ny(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalinger = listOf(),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            clock = fixedClock,
            beregning = createBeregning(1.mai(2021), 31.desember(2021)),
            uføregrunnlag = uføreList,
        ).generate()

        actual.utbetalingslinjer.size shouldBe 1
        actual shouldBe expectedUtbetaling(
            actual = actual,
            oppdragslinjer = nonEmptyListOf(
                expectedUtbetalingslinje(
                    utbetalingslinjeId = actual.utbetalingslinjer.first().id,
                    opprettet = actual.utbetalingslinjer.first().opprettet,
                    fraOgMed = 1.mai(2021),
                    tilOgMed = 31.desember(2021),
                    beløp = 21989,
                    forrigeUtbetalingslinjeId = null,
                    uføregrad = Uføregrad.parse(50),
                ),
            ),
        )
    }

    @Test
    fun `mapper flere uføregrader til riktig utbetalingslinje for periode (1 til mange)`() {
        val uføreList = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.mai(2021)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.juni(2021), tilOgMed = 31.desember(2021)),
                uføregrad = Uføregrad.parse(70),
                forventetInntekt = 0,
            ),
        )

        val actual = Utbetalingsstrategi.Ny(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalinger = listOf(),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            clock = fixedClock,
            beregning = createBeregning(1.mai(2021), 31.desember(2021)),
            uføregrunnlag = uføreList,
        ).generate()

        actual.utbetalingslinjer.size shouldBe 2
        actual shouldBe expectedUtbetaling(
            actual = actual,
            oppdragslinjer = nonEmptyListOf(
                expectedUtbetalingslinje(
                    utbetalingslinjeId = actual.utbetalingslinjer.first().id,
                    opprettet = actual.utbetalingslinjer.first().opprettet,
                    fraOgMed = 1.mai(2021),
                    tilOgMed = 31.mai(2021),
                    beløp = 21989,
                    forrigeUtbetalingslinjeId = null,
                    uføregrad = Uføregrad.parse(50),
                ),
                expectedUtbetalingslinje(
                    utbetalingslinjeId = actual.utbetalingslinjer.last().id,
                    opprettet = actual.utbetalingslinjer.last().opprettet,
                    fraOgMed = 1.juni(2021),
                    tilOgMed = 31.desember(2021),
                    beløp = 21989,
                    forrigeUtbetalingslinjeId = actual.utbetalingslinjer.first().id,
                    uføregrad = Uføregrad.parse(70),
                ),
            ),
        )
    }

    @Test
    fun `mapper flere beregningsperioder til ufregrunnlag (mange til 1)`() {
        val periode = Periode.create(1.januar(2021), 31.desember(2021))
        val uføreList = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
        )
        val actual = Utbetalingsstrategi.Ny(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalinger = listOf(),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            clock = fixedClock,
            beregning = BeregningFactory(clock = fixedClock).ny(
                fradrag = listOf(
                    FradragFactory.ny(
                        type = Fradragstype.ForventetInntekt,
                        månedsbeløp = 1000.0,
                        periode = periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    FradragFactory.ny(
                        type = Fradragstype.Sosialstønad,
                        månedsbeløp = 5000.0,
                        periode = Periode.create(1.januar(2021), 31.mai(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    FradragFactory.ny(
                        type = Fradragstype.Kontantstøtte,
                        månedsbeløp = 8000.0,
                        periode = Periode.create(1.juni(2021), 31.desember(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
                beregningsperioder = listOf(
                    Beregningsperiode(
                        periode = periode,
                        strategy = BeregningStrategy.BorAlene,
                    )
                )
            ),
            uføregrunnlag = uføreList,
        ).generate()

        actual.utbetalingslinjer.size shouldBe 3
        actual shouldBe expectedUtbetaling(
            actual = actual,
            oppdragslinjer = nonEmptyListOf(
                expectedUtbetalingslinje(
                    utbetalingslinjeId = actual.utbetalingslinjer.first().id,
                    opprettet = actual.utbetalingslinjer.first().opprettet,
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 30.april(2021),
                    beløp = 14946,
                    forrigeUtbetalingslinjeId = null,
                    uføregrad = Uføregrad.parse(50),
                ),
                expectedUtbetalingslinje(
                    utbetalingslinjeId = actual.utbetalingslinjer[1].id,
                    opprettet = actual.utbetalingslinjer[1].opprettet,
                    fraOgMed = 1.mai(2021),
                    tilOgMed = 31.mai(2021),
                    beløp = 15989,
                    forrigeUtbetalingslinjeId = actual.utbetalingslinjer.first().id,
                    uføregrad = Uføregrad.parse(50),
                ),
                expectedUtbetalingslinje(
                    utbetalingslinjeId = actual.utbetalingslinjer.last().id,
                    opprettet = actual.utbetalingslinjer.last().opprettet,
                    fraOgMed = 1.juni(2021),
                    tilOgMed = 31.desember(2021),
                    beløp = 12989,
                    forrigeUtbetalingslinjeId = actual.utbetalingslinjer[1].id,
                    uføregrad = Uføregrad.parse(50),
                ),
            ),
        )
    }

    @Test
    fun `mapper flere beregningsperioder til flere ufregrunnlag (mange til mange)`() {
        val periode = Periode.create(1.januar(2021), 31.desember(2021))
        val uføreList = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.mai(2021)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.juni(2021), tilOgMed = 31.desember(2021)),
                uføregrad = Uføregrad.parse(70),
                forventetInntekt = 0,
            ),
        )
        val actual = Utbetalingsstrategi.Ny(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalinger = listOf(),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            clock = fixedClock,
            beregning = BeregningFactory(clock = fixedClock).ny(
                fradrag = listOf(
                    FradragFactory.ny(
                        type = Fradragstype.ForventetInntekt,
                        månedsbeløp = 1000.0,
                        periode = periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    FradragFactory.ny(
                        type = Fradragstype.Sosialstønad,
                        månedsbeløp = 5000.0,
                        periode = Periode.create(1.januar(2021), 31.mai(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    FradragFactory.ny(
                        type = Fradragstype.Kontantstøtte,
                        månedsbeløp = 8000.0,
                        periode = Periode.create(1.juni(2021), 31.desember(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
                beregningsperioder = listOf(
                    Beregningsperiode(
                        periode = periode,
                        strategy = BeregningStrategy.BorAlene,
                    )
                )
            ),
            uføregrunnlag = uføreList,
        ).generate()

        actual.utbetalingslinjer.size shouldBe 3
        actual shouldBe expectedUtbetaling(
            actual = actual,
            oppdragslinjer = nonEmptyListOf(
                expectedUtbetalingslinje(
                    utbetalingslinjeId = actual.utbetalingslinjer.first().id,
                    opprettet = actual.utbetalingslinjer.first().opprettet,
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 30.april(2021),
                    beløp = 14946,
                    forrigeUtbetalingslinjeId = null,
                    uføregrad = Uføregrad.parse(50),
                ),
                expectedUtbetalingslinje(
                    utbetalingslinjeId = actual.utbetalingslinjer[1].id,
                    opprettet = actual.utbetalingslinjer[1].opprettet,
                    fraOgMed = 1.mai(2021),
                    tilOgMed = 31.mai(2021),
                    beløp = 15989,
                    forrigeUtbetalingslinjeId = actual.utbetalingslinjer.first().id,
                    uføregrad = Uføregrad.parse(50),
                ),
                expectedUtbetalingslinje(
                    utbetalingslinjeId = actual.utbetalingslinjer.last().id,
                    opprettet = actual.utbetalingslinjer.last().opprettet,
                    fraOgMed = 1.juni(2021),
                    tilOgMed = 31.desember(2021),
                    beløp = 12989,
                    forrigeUtbetalingslinjeId = actual.utbetalingslinjer[1].id,
                    uføregrad = Uføregrad.parse(70),
                ),
            ),
        )
    }

    @Test
    fun `kaster exception hvis uføregrunnalget ikke inneholder alle beregningsperiodene`() {
        val uføreList = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.mai(2021)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
        )

        shouldThrow<Utbetalingsstrategi.UtbetalingStrategyException> {
            Utbetalingsstrategi.Ny(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalinger = listOf(),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                clock = fixedClock,
                beregning = createBeregning(1.januar(2021), 31.desember(2021)),
                uføregrunnlag = uføreList,
            ).generate()
        }.also {
            it.message shouldContain "Uføregrunnlaget inneholder ikke alle beregningsperiodene. Grunnlagsperiodene:"
        }
    }

    @Test
    fun `må eksistere uføreperiode for alle månedsberegninger`() {
        val uføreList = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.mai(2021)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.juni(2021), tilOgMed = 31.desember(2021)),
                uføregrad = Uføregrad.parse(70),
                forventetInntekt = 0,
            ),
        )

        val actual = Utbetalingsstrategi.Ny(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalinger = listOf(),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            clock = fixedClock,
            beregning = createBeregning(1.juni(2021), 31.desember(2021)),
            uføregrunnlag = uføreList,
        ).generate()

        actual.utbetalingslinjer.size shouldBe 1
        actual shouldBe expectedUtbetaling(
            actual = actual,
            oppdragslinjer = nonEmptyListOf(
                expectedUtbetalingslinje(
                    utbetalingslinjeId = actual.utbetalingslinjer.first().id,
                    opprettet = actual.utbetalingslinjer.first().opprettet,
                    fraOgMed = 1.juni(2021),
                    tilOgMed = 31.desember(2021),
                    beløp = 21989,
                    forrigeUtbetalingslinjeId = null,
                    uføregrad = Uføregrad.parse(70),
                ),
            ),
        )
    }

    @Test
    fun `utbetalingslinje med uføregrad følger beregningsperiode`() {
        val uføreList = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.mai(2021)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(fraOgMed = 1.juni(2021), tilOgMed = 31.desember(2021)),
                uføregrad = Uføregrad.parse(70),
                forventetInntekt = 0,
            ),
        )

        val actual = Utbetalingsstrategi.Ny(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalinger = listOf(),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            clock = fixedClock,
            beregning = createBeregning(1.juni(2021), 30.november(2021)),
            uføregrunnlag = uføreList,
        ).generate()

        actual.utbetalingslinjer.size shouldBe 1
        actual shouldBe expectedUtbetaling(
            actual = actual,
            oppdragslinjer = nonEmptyListOf(
                expectedUtbetalingslinje(
                    utbetalingslinjeId = actual.utbetalingslinjer.first().id,
                    opprettet = actual.utbetalingslinjer.first().opprettet,
                    fraOgMed = 1.juni(2021),
                    tilOgMed = 30.november(2021),
                    beløp = 21989,
                    forrigeUtbetalingslinjeId = null,
                    uføregrad = Uføregrad.parse(70),
                ),
            ),
        )
    }

    private fun expectedUtbetaling(
        actual: Utbetaling.UtbetalingForSimulering,
        oppdragslinjer: NonEmptyList<Utbetalingslinje>,
    ): Utbetaling.UtbetalingForSimulering {
        return Utbetaling.UtbetalingForSimulering(
            id = actual.id,
            opprettet = actual.opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            utbetalingslinjer = oppdragslinjer,
            fnr = fnr,
            type = Utbetaling.UtbetalingsType.NY,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            avstemmingsnøkkel = Avstemmingsnøkkel(fixedTidspunkt),
        )
    }

    private fun expectedUtbetalingslinje(
        utbetalingslinjeId: UUID30,
        opprettet: Tidspunkt,
        fraOgMed: LocalDate,
        tilOgMed: LocalDate,
        beløp: Int,
        forrigeUtbetalingslinjeId: UUID30?,
        uføregrad: Uføregrad = Uføregrad.parse(50),
    ): Utbetalingslinje {
        return Utbetalingslinje.Ny(
            id = utbetalingslinjeId,
            opprettet = opprettet,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
            beløp = beløp,
            uføregrad = uføregrad,
        )
    }

    private fun createBeregning(fraOgMed: LocalDate, tilOgMed: LocalDate) = BeregningFactory(clock = fixedClock).ny(
        fradrag = listOf(
            FradragFactory.ny(
                type = Fradragstype.ForventetInntekt,
                månedsbeløp = 0.0,
                periode = Periode.create(fraOgMed = fraOgMed, tilOgMed = tilOgMed),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        ),
        beregningsperioder = listOf(
            Beregningsperiode(
                periode = Periode.create(fraOgMed, tilOgMed),
                strategy = BeregningStrategy.BorAlene,
            )
        )
    )
}
