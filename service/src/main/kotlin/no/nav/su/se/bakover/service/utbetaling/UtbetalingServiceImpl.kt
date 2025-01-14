package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingKlargjortForOversendelse
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerUtbetalingForPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.Utbetalinger
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingslinjePåTidslinje
import org.slf4j.LoggerFactory
import økonomi.domain.kvittering.Kvittering
import java.time.LocalDate
import java.util.UUID

class UtbetalingServiceImpl(
    private val utbetalingRepo: UtbetalingRepo,
    private val simuleringClient: SimuleringClient,
    private val utbetalingPublisher: UtbetalingPublisher,
) : UtbetalingService {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentUtbetalingerForSakId(sakId: UUID): Utbetalinger {
        return utbetalingRepo.hentOversendteUtbetalinger(sakId)
    }

    override fun oppdaterMedKvittering(
        utbetalingId: UUID30,
        kvittering: Kvittering,
        sessionContext: SessionContext?,
    ): Either<FantIkkeUtbetaling, Utbetaling.OversendtUtbetaling.MedKvittering> {
        return utbetalingRepo.hentOversendtUtbetalingForUtbetalingId(utbetalingId, sessionContext)
            ?.let { utbetaling ->
                when (utbetaling) {
                    is Utbetaling.OversendtUtbetaling.MedKvittering -> {
                        log.info("Kvittering er allerede mottatt for utbetaling: ${utbetaling.id}")
                        utbetaling
                    }

                    is Utbetaling.OversendtUtbetaling.UtenKvittering -> {
                        log.info("Oppdaterer utbetaling med kvittering fra Oppdrag")
                        utbetaling.toKvittertUtbetaling(kvittering).also {
                            utbetalingRepo.oppdaterMedKvittering(it, sessionContext)
                        }
                    }
                }.right()
            } ?: FantIkkeUtbetaling.left()
            .also { log.warn("Fant ikke utbetaling med id: $utbetalingId") }
    }

    override fun simulerUtbetaling(utbetaling: Utbetaling.UtbetalingForSimulering, simuleringsperiode: Periode): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling> {
        return simulerUtbetaling(
            SimulerUtbetalingForPeriode(
                utbetaling = utbetaling,
                simuleringsperiode = simuleringsperiode,
            ),
        )
    }

    override fun hentGjeldendeUtbetaling(
        sakId: UUID,
        forDato: LocalDate,
    ): Either<Utbetalinger.FantIkkeGjeldendeUtbetaling, UtbetalingslinjePåTidslinje> {
        return hentUtbetalingerForSakId(sakId).hentGjeldendeUtbetaling(
            forDato,
        )
    }

    override fun klargjørUtbetaling(
        utbetaling: Utbetaling.SimulertUtbetaling,
        transactionContext: TransactionContext,
    ): Either<UtbetalingFeilet, UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>> {
        return UtbetalingKlargjortForOversendelse(
            utbetaling = utbetaling.forberedOversendelse(transactionContext),
            callback = { utbetalingsrequest ->
                sendUtbetalingTilOS(utbetalingsrequest)
                    .mapLeft { UtbetalingFeilet.Protokollfeil }
            },
        ).right()
    }

    private fun simulerUtbetaling(
        request: no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerUtbetalingRequest,
    ): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling> {
        return simuleringClient.simulerUtbetaling(request = request)
            .map { request.utbetaling.toSimulertUtbetaling(it) }
    }

    private fun sendUtbetalingTilOS(utbetalingsRequest: Utbetalingsrequest): Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest> {
        return utbetalingPublisher.publishRequest(utbetalingsRequest)
            .mapLeft {
                UtbetalingFeilet.Protokollfeil
            }
    }

    private fun Utbetaling.SimulertUtbetaling.forberedOversendelse(transactionContext: TransactionContext): Utbetaling.OversendtUtbetaling.UtenKvittering {
        return toOversendtUtbetaling(utbetalingPublisher.generateRequest(this)).also {
            utbetalingRepo.opprettUtbetaling(
                utbetaling = it,
                transactionContext = transactionContext,
            )
        }
    }
}
