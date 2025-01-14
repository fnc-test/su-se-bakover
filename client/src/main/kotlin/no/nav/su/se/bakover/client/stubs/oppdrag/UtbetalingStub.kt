package no.nav.su.se.bakover.client.stubs.oppdrag

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.client.oppdrag.utbetaling.toUtbetalingRequest
import no.nav.su.se.bakover.common.infrastructure.xml.xmlMapper
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher

data object UtbetalingStub : UtbetalingPublisher {

    override fun publishRequest(utbetalingsrequest: Utbetalingsrequest): Either<UtbetalingPublisher.KunneIkkeSendeUtbetaling, Utbetalingsrequest> {
        return utbetalingsrequest.right()
    }

    override fun generateRequest(utbetaling: Utbetaling.SimulertUtbetaling): Utbetalingsrequest {
        return Utbetalingsrequest(value = xmlMapper.writeValueAsString(toUtbetalingRequest(utbetaling)))
    }
}
