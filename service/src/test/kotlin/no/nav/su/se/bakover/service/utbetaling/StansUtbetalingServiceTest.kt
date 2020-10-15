// package no.nav.su.se.bakover.service.utbetaling
//
// import arrow.core.left
// import arrow.core.right
// import com.nhaarman.mockitokotlin2.any
// import com.nhaarman.mockitokotlin2.capture
// import com.nhaarman.mockitokotlin2.doAnswer
// import com.nhaarman.mockitokotlin2.doReturn
// import com.nhaarman.mockitokotlin2.eq
// import com.nhaarman.mockitokotlin2.mock
// import com.nhaarman.mockitokotlin2.verify
// import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
// import io.kotest.matchers.shouldBe
// import no.nav.su.se.bakover.common.Tidspunkt
// import no.nav.su.se.bakover.common.UUID30
// import no.nav.su.se.bakover.domain.Attestant
// import no.nav.su.se.bakover.domain.Fnr
// import no.nav.su.se.bakover.domain.Sak
// import no.nav.su.se.bakover.domain.Saksbehandler
// import no.nav.su.se.bakover.domain.oppdrag.Kvittering
// import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
// import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
// import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
// import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding.Oppdragsmeldingstatus.FEIL
// import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding.Oppdragsmeldingstatus.SENDT
// import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
// import no.nav.su.se.bakover.domain.oppdrag.UtbetalingV2
// import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
// import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
// import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
// import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
// import no.nav.su.se.bakover.domain.oppdrag.toOversendtUtbetaling
// import no.nav.su.se.bakover.domain.oppdrag.toSimulertUtbetaling
// import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
// import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher.KunneIkkeSendeUtbetaling
// import no.nav.su.se.bakover.service.argShouldBe
// import no.nav.su.se.bakover.service.sak.SakService
// import org.junit.jupiter.api.Test
// import org.mockito.ArgumentCaptor
// import org.mockito.internal.verification.Times
// import org.mockito.stubbing.Answer
// import java.time.Clock
// import java.time.Instant
// import java.time.LocalDate
// import java.time.ZoneOffset
// import java.util.UUID
//
// internal class StansUtbetalingServiceTest {
//
//     @Test
//     fun `stans utbetalinger`() {
//         val setup = Setup()
//
//         val sakServiceMock = mock<SakService> {
//             on { hentSak(argShouldBe(setup.sakId)) } doReturn setup.eksisterendeSak.right()
//         }
//
//         val capturedNyUtbetaling = ArgumentCaptor.forClass(NyUtbetaling::class.java)
//         val capturedOpprettUtbetaling = ArgumentCaptor.forClass(Utbetaling.Stans::class.java)
//         val capturedAddSimulering = ArgumentCaptor.forClass(Simulering::class.java)
//         val capturedAddOppdragsmelding = ArgumentCaptor.forClass(Oppdragsmelding::class.java)
//         val utbetalingServiceMock = mock<UtbetalingService> {
//             on {
//                 simulerUtbetaling(capture<NyUtbetaling>(capturedNyUtbetaling))
//             } doAnswer (
//                 Answer { invocation ->
//                     val actualNyUtbetaling =
//                         capturedNyUtbetaling.value.utbetaling as UtbetalingV2.UtbetalingForSimulering
//                     actualNyUtbetaling shouldBe setup.forventetUtbetalingForSimulering(actualNyUtbetaling)
//                     actualNyUtbetaling.toSimulertUtbetaling(simulering = setup.nySimulering).right()
//                 }
//                 )
//             on {
//                 opprettUtbetaling(any(), capture<Utbetaling.Stans>(capturedOpprettUtbetaling))
//             } doAnswer { capturedOpprettUtbetaling.value }
//             on {
//                 addSimulering(any(), capture<Simulering>(capturedAddSimulering))
//             } doAnswer {
//                 capturedOpprettUtbetaling.value.copy(
//                     simulering = capturedAddSimulering.value
//                 )
//             }
//             on {
//                 addOppdragsmelding(any(), capture<Oppdragsmelding>(capturedAddOppdragsmelding))
//             } doAnswer {
//                 capturedOpprettUtbetaling.value.copy(
//                     simulering = capturedAddSimulering.value,
//                     oppdragsmelding = capturedAddOppdragsmelding.value
//                 )
//             }
//         }
//
//         val capturedUtbetalingArgument = ArgumentCaptor.forClass(NyUtbetaling::class.java)
//         val publisherMock = mock<UtbetalingPublisher> {
//             on {
//                 publish(capture<NyUtbetaling>(capturedUtbetalingArgument))
//             } doAnswer (
//                 Answer { invocation ->
//                     val capture = capturedUtbetalingArgument.value.utbetaling as UtbetalingV2.SimulertUtbetaling
//                     capture shouldBe setup.forventetSimulertUtbetaling(capture)
//                     setup.oppdragsmeldingSendt.right()
//                 }
//                 )
//         }
//
//         val service = StansUtbetalingService(
//             clock = setup.clock,
//             utbetalingPublisher = publisherMock,
//             utbetalingService = utbetalingServiceMock,
//             sakService = sakServiceMock
//         )
//
//         // val actualSak = service.stansUtbetalinger(sakId = setup.eksisterendeSak.id, Saksbehandler("AB12345")).orNull()!!
//         val expectedUtbetaling = setup.forventetOversendtUtbetaling(
//             ((capturedUtbetalingArgument.value.utbetaling) as UtbetalingV2.SimulertUtbetaling).toOversendtUtbetaling(setup.oppdragsmeldingSendt))
//
//         // val expectedSak = setup.eksisterendeSak.copy(
//         //     oppdrag = setup.eksisterendeOppdrag.copy(
//         //         utbetalinger = setup.eksisterendeOppdrag.hentUtbetalinger() + expectedUtbetaling
//         //     )
//         // )
//         // actualSak shouldBe expectedSak
//
//         verify(utbetalingServiceMock, Times(1)).simulerUtbetaling(any())
//         verify(publisherMock, Times(1)).publish(any())
//         verify(utbetalingServiceMock, Times(1)).opprettUtbetaling(eq(setup.oppdragId), expectedUtbetaling)
//         verify(utbetalingServiceMock, Times(1)).addSimulering(any(), any())
//         verify(utbetalingServiceMock, Times(1)).addOppdragsmelding(any(), any())
//
//         verifyNoMoreInteractions(publisherMock, utbetalingServiceMock)
//     }
//
//     @Test
//     fun `Sjekk at vi svarer furnuftig når simulering feiler`() {
//         val setup = Setup()
//
//         val sakServiceMock = mock<SakService> {
//             on { hentSak(argShouldBe(setup.sakId)) } doReturn setup.eksisterendeSak.right()
//         }
//
//         val utbetalingServiceMock: UtbetalingService = mock() {
//             on {
//                 simulerUtbetaling(any())
//             } doAnswer (Answer { SimuleringFeilet.TEKNISK_FEIL.left() })
//         }
//
//         val service = StansUtbetalingService(
//             clock = setup.clock,
//             utbetalingPublisher = mock(),
//             utbetalingService = utbetalingServiceMock,
//             sakService = sakServiceMock
//         )
//
//         val actualResponse =
//             service.stansUtbetalinger(sakId = setup.eksisterendeSak.id, saksbehandler = Saksbehandler("AB12345"))
//
//         actualResponse shouldBe StansUtbetalingService.KunneIkkeStanseUtbetalinger.left()
//
//         verify(utbetalingServiceMock, Times(1)).simulerUtbetaling(any())
//
//         verifyNoMoreInteractions(utbetalingServiceMock)
//     }
//
//     @Test
//     fun `svarer med feil dersom simulering inneholder beløp større enn 0`() {
//         val setup = Setup()
//
//         val sakServiceMock = mock<SakService> {
//             on { hentSak(argShouldBe(setup.sakId)) } doReturn setup.eksisterendeSak.right()
//         }
//
//         val utbetalingServiceMock: UtbetalingService = mock() {
//             on {
//                 simulerUtbetaling(any())
//             } doAnswer (Answer { setup.nySimulering.copy(nettoBeløp = 6000).right() })
//         }
//
//         val service = StansUtbetalingService(
//             clock = setup.clock,
//             utbetalingPublisher = mock(),
//             utbetalingService = utbetalingServiceMock,
//             sakService = sakServiceMock
//         )
//
//         val actualResponse =
//             service.stansUtbetalinger(sakId = setup.eksisterendeSak.id, saksbehandler = Saksbehandler("AB12345"))
//
//         actualResponse shouldBe StansUtbetalingService.KunneIkkeStanseUtbetalinger.left()
//
//         verify(utbetalingServiceMock, Times(1)).simulerUtbetaling(any())
//
//         verifyNoMoreInteractions(utbetalingServiceMock)
//     }
//
//     @Test
//     fun `Sjekk at vi svarer furnuftig når publisering feiler`() {
//         val setup = Setup()
//
//         val sakServiceMock = mock<SakService> {
//             on { hentSak(argShouldBe(setup.sakId)) } doReturn setup.eksisterendeSak.right()
//         }
//
//         val capturedOpprettUtbetaling = ArgumentCaptor.forClass(Utbetaling.Stans::class.java)
//         val capturedAddSimulering = ArgumentCaptor.forClass(Simulering::class.java)
//         val utbetalingServiceMock = mock<UtbetalingService> {
//             on {
//                 simulerUtbetaling(any())
//             } doAnswer (Answer { setup.nySimulering.right() })
//             on {
//                 opprettUtbetaling(any(), capture<Utbetaling.Stans>(capturedOpprettUtbetaling))
//             } doAnswer { capturedOpprettUtbetaling.value }
//             on {
//                 addSimulering(any(), capture<Simulering>(capturedAddSimulering))
//             } doAnswer {
//                 capturedOpprettUtbetaling.value.copy(
//                     simulering = capturedAddSimulering.value
//                 )
//             }
//         }
//
//         val publisherMock = mock<UtbetalingPublisher> {
//             on {
//                 publish(any())
//             } doAnswer (
//                 Answer {
//                     KunneIkkeSendeUtbetaling(Oppdragsmelding(SENDT, "", Avstemmingsnøkkel())).left()
//                 }
//                 )
//         }
//
//         val service = StansUtbetalingService(
//             clock = setup.clock,
//             utbetalingPublisher = publisherMock,
//             utbetalingService = utbetalingServiceMock,
//             sakService = sakServiceMock
//         )
//
//         val actualResponse =
//             service.stansUtbetalinger(sakId = setup.eksisterendeSak.id, saksbehandler = Saksbehandler("AB12345"))
//
//         actualResponse shouldBe StansUtbetalingService.KunneIkkeStanseUtbetalinger.left()
//
//         verify(utbetalingServiceMock, Times(1)).simulerUtbetaling(any())
//         verify(publisherMock, Times(1)).publish(any())
//         // verify(utbetalingServiceMock, Times(1)).opprettUtbetaling(eq(setup.oppdragId), any())
//         verify(utbetalingServiceMock, Times(1)).addSimulering(any(), any())
//         verify(utbetalingServiceMock, Times(1)).addOppdragsmelding(any(), any())
//
//         verifyNoMoreInteractions(publisherMock, utbetalingServiceMock)
//     }
//
//     private data class Setup(
//         val clock: Clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
//         val sakId: UUID = UUID.randomUUID(),
//         val fnr: Fnr = Fnr("12345678910"),
//         val attestant: Attestant = Attestant("AB12345"),
//         val oppdragId: UUID30 = UUID30.randomUUID(),
//         val utbetalingId: UUID30 = UUID30.randomUUID(),
//         val eksisterendeUtbetaling: Utbetaling = Utbetaling.Ny(
//             id = UUID30.randomUUID(),
//             opprettet = Tidspunkt.EPOCH,
//             simulering = Simulering(
//                 gjelderId = fnr,
//                 gjelderNavn = "",
//                 datoBeregnet = LocalDate.EPOCH,
//                 nettoBeløp = 10000,
//                 periodeList = listOf()
//             ),
//             kvittering = Kvittering(
//                 utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
//                 originalKvittering = "<someXml></someXml>",
//                 mottattTidspunkt = Tidspunkt.EPOCH
//             ),
//             oppdragsmelding = Oppdragsmelding(SENDT, "", Avstemmingsnøkkel()),
//             utbetalingslinjer = listOf(
//                 Utbetalingslinje(
//                     id = UUID30.randomUUID(),
//                     opprettet = Tidspunkt.EPOCH,
//                     fraOgMed = LocalDate.EPOCH,
//                     tilOgMed = LocalDate.EPOCH.plusMonths(12),
//                     forrigeUtbetalingslinjeId = null,
//                     beløp = 10000
//                 )
//             ),
//             avstemmingId = null,
//             fnr = fnr
//         ),
//         val eksisterendeOppdrag: Oppdrag = Oppdrag(
//             id = oppdragId,
//             opprettet = Tidspunkt.EPOCH,
//             sakId = sakId,
//             utbetalinger = mutableListOf(
//                 eksisterendeUtbetaling
//             )
//         ),
//         val nySimulering: Simulering = Simulering(
//             gjelderId = fnr,
//             gjelderNavn = "",
//             datoBeregnet = LocalDate.EPOCH,
//             nettoBeløp = 0,
//             periodeList = listOf()
//         ),
//         val eksisterendeSak: Sak = Sak(
//             id = sakId,
//             opprettet = Tidspunkt.EPOCH,
//             fnr = fnr,
//             oppdrag = eksisterendeOppdrag
//         ),
//         val oppdragsmeldingSendt: Oppdragsmelding = Oppdragsmelding(SENDT, "", Avstemmingsnøkkel()),
//         val oppdragsmeldingFeil: Oppdragsmelding = Oppdragsmelding(FEIL, "", Avstemmingsnøkkel())
//     ) {
//
//         fun forventetSimulertUtbetaling(
//             actualUtbetaling: UtbetalingV2.SimulertUtbetaling
//         ) = UtbetalingV2.SimulertUtbetaling(
//             id = actualUtbetaling.id,
//             opprettet = actualUtbetaling.opprettet,
//             utbetalingslinjer = listOf(
//                 Utbetalingslinje(
//                     id = actualUtbetaling.utbetalingslinjer[0].id,
//                     opprettet = actualUtbetaling.utbetalingslinjer[0].opprettet,
//                     fraOgMed = LocalDate.of(1970, 2, 1),
//                     tilOgMed = LocalDate.of(1971, 1, 1),
//                     forrigeUtbetalingslinjeId = eksisterendeUtbetaling.utbetalingslinjer[0].id,
//                     beløp = 0
//                 )
//             ),
//             fnr = fnr,
//             type = Utbetaling.UtbetalingType.STANS,
//             simulering = nySimulering
//         )
//
//         fun forventetUtbetalingForSimulering(
//             actualUtbetaling: UtbetalingV2.UtbetalingForSimulering
//         ) = UtbetalingV2.UtbetalingForSimulering(
//             id = actualUtbetaling.id,
//             opprettet = actualUtbetaling.opprettet,
//             utbetalingslinjer = listOf(
//                 Utbetalingslinje(
//                     id = actualUtbetaling.utbetalingslinjer[0].id,
//                     opprettet = actualUtbetaling.utbetalingslinjer[0].opprettet,
//                     fraOgMed = LocalDate.of(1970, 2, 1),
//                     tilOgMed = LocalDate.of(1971, 1, 1),
//                     forrigeUtbetalingslinjeId = eksisterendeUtbetaling.utbetalingslinjer[0].id,
//                     beløp = 0
//                 )
//             ),
//             fnr = fnr,
//             type = Utbetaling.UtbetalingType.STANS
//         )
//
//         fun forventetOversendtUtbetaling(
//             actualUtbetaling: UtbetalingV2.OversendtUtbetaling,
//         ) = UtbetalingV2.OversendtUtbetaling(
//             id = actualUtbetaling.id,
//             opprettet = actualUtbetaling.opprettet,
//             utbetalingslinjer = listOf(
//                 Utbetalingslinje(
//                     id = actualUtbetaling.utbetalingslinjer[0].id,
//                     opprettet = actualUtbetaling.utbetalingslinjer[0].opprettet,
//                     fraOgMed = LocalDate.of(1970, 2, 1),
//                     tilOgMed = LocalDate.of(1971, 1, 1),
//                     forrigeUtbetalingslinjeId = eksisterendeUtbetaling.utbetalingslinjer[0].id,
//                     beløp = 0
//                 )
//             ),
//             fnr = fnr,
//             simulering = actualUtbetaling.simulering,
//             oppdragsmelding = actualUtbetaling.oppdragsmelding,
//             type = Utbetaling.UtbetalingType.STANS
//         )
//     }
// }
