package no.nav.su.se.bakover.client.oppdrag.avstemming

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

internal class AvstemmingDataBuilderTest {
    val zoneId = ZoneId.of("Europe/Oslo")
    @Test
    fun `Sjekk at vi bygger AvstemmingDataReqest riktig`() {
        val avstemmingId = UUID30.randomUUID()
        val expected = AvstemmingDataRequest(
            aksjon = Aksjonsdata(
                aksjonType = Aksjonsdata.AksjonType.DATA,
                kildeType = Aksjonsdata.KildeType.AVLEVERT,
                avstemmingType = Aksjonsdata.AvstemmingType.GRENSESNITTAVSTEMMING,
                mottakendeKomponentKode = "OS",
                brukerId = "SU",
                nokkelFom = "2020-03-01-00.00.00.000000",
                nokkelTom = "2020-03-02-00.00.00.000000",
                avleverendeAvstemmingId = avstemmingId.toString()
            ),
            total = AvstemmingDataRequest.Totaldata(
                totalAntall = 4,
                totalBelop = BigDecimal(13000),
                fortegn = AvstemmingDataRequest.Fortegn.TILLEGG
            ),
            periode = AvstemmingDataRequest.Periodedata(
                datoAvstemtFom = "2020030100",
                datoAvstemtTom = "2020030200"
            ),
            grunnlag = AvstemmingDataRequest.Grunnlagdata(
                godkjentAntall = 2,
                godkjentBelop = BigDecimal("1600"),
                godkjenttFortegn = AvstemmingDataRequest.Fortegn.TILLEGG,
                varselAntall = 1,
                varselBelop = BigDecimal(1400),
                varselFortegn = AvstemmingDataRequest.Fortegn.TILLEGG,
                avvistAntall = 1,
                avvistBelop = BigDecimal(10000),
                avvistFortegn = AvstemmingDataRequest.Fortegn.TILLEGG,
                manglerAntall = 0,
                manglerBelop = BigDecimal(0),
                manglerFortegn = AvstemmingDataRequest.Fortegn.TILLEGG
            ),
            detalj = emptyList()
        )

        val listUtbetaling = mutableListOf<Utbetaling>()

        listUtbetaling.add(
            lagUtbetaling(
                1.mars(2020),
                Kvittering.Utbetalingsstatus.OK,
                listOf(
                    lagUtbetalingLinje(1.mars(2020), 31.mars(2020), 100),
                    lagUtbetalingLinje(1.april(2020), 30.april(2020), 200)
                )
            )
        )
        listUtbetaling.add(
            lagUtbetaling(
                1.mars(2020),
                Kvittering.Utbetalingsstatus.OK,
                listOf(
                    lagUtbetalingLinje(1.mars(2020), 31.mars(2020), 600),
                    lagUtbetalingLinje(1.april(2020), 30.april(2020), 700)
                )
            )
        )
        listUtbetaling.add(
            lagUtbetaling(
                2.mars(2020),
                Kvittering.Utbetalingsstatus.OK_MED_VARSEL,
                listOf(
                    lagUtbetalingLinje(1.mars(2020), 31.mars(2020), 400),
                    lagUtbetalingLinje(1.april(2020), 30.april(2020), 500),
                    lagUtbetalingLinje(1.mai(2020), 31.mai(2020), 500)
                )
            )
        )
        listUtbetaling.add(
            lagUtbetaling(
                1.mars(2020),
                Kvittering.Utbetalingsstatus.FEIL,
                listOf(
                    lagUtbetalingLinje(1.mars(2020), 31.mars(2020), 1000),
                    lagUtbetalingLinje(1.april(2020), 30.april(2020), 2000),
                    lagUtbetalingLinje(1.mai(2020), 31.mai(2020), 3000),
                    lagUtbetalingLinje(1.juni(2020), 30.juni(2020), 4000)
                )
            )
        )

        AvstemmingDataBuilder(
            Avstemming(
                id = avstemmingId,
                opprettet = now(),
                fom = 1.mars(2020).atStartOfDay(zoneId).toInstant(),
                tom = 2.mars(2020).atStartOfDay(zoneId).toInstant(),
                utbetalinger = listUtbetaling,
                avstemmingXmlRequest = null
            ),
        ).build() shouldBe expected
    }

    private fun lagUtbetalingLinje(fom: LocalDate, tom: LocalDate, beløp: Int) = Utbetalingslinje(
        id = UUID30.randomUUID(),
        opprettet = fom.atStartOfDay(zoneId).toInstant(),
        fom = fom,
        tom = tom,
        forrigeUtbetalingslinjeId = null,
        beløp = beløp
    )

    private fun lagUtbetaling(opprettet: LocalDate, status: Kvittering.Utbetalingsstatus, linjer: List<Utbetalingslinje>) =
        Utbetaling(
            id = UUID30.randomUUID(),
            opprettet = opprettet.atStartOfDay(zoneId).toInstant(),
            simulering = null,
            kvittering = Kvittering(utbetalingsstatus = status, originalKvittering = "hallo", mottattTidspunkt = now()),
            oppdragsmelding = Oppdragsmelding(Oppdragsmelding.Oppdragsmeldingstatus.SENDT, "Melding"),
            utbetalingslinjer = linjer
        )
}
