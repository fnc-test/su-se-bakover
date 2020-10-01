package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.database.beregning.BeregningRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepo
import no.nav.su.se.bakover.database.oppdrag.OppdragRepo
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.Fradrag
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import java.time.LocalDate
import java.util.UUID

internal class BehandlingServiceImpl(
    private val behandlingRepo: BehandlingRepo,
    private val hendelsesloggRepo: HendelsesloggRepo,
    private val beregningRepo: BeregningRepo,
    private val objectRepo: ObjectRepo, // TODO dont use
    private val oppdragRepo: OppdragRepo,
    private val simuleringClient: SimuleringClient,
    private val utbetalingService: UtbetalingService // TODO use services or repos? probably services
) : BehandlingService {

    override fun underkjenn(
        begrunnelse: String,
        attestant: Attestant,
        behandling: Behandling
    ): Either<Behandling.KunneIkkeUnderkjenne, Behandling> {
        return behandling.underkjenn(begrunnelse, attestant)
            .mapLeft { it }
            .map {
                hendelsesloggRepo.oppdaterHendelseslogg(it.hendelseslogg)
                behandlingRepo.hentBehandling(it.id)!!
            }
    }

    override fun oppdaterBehandlingsinformasjon(
        behandlingId: UUID,
        behandlingsinformasjon: Behandlingsinformasjon
    ): Behandling {
        val beforeUpdate = behandlingRepo.hentBehandling(behandlingId)!!
        beregningRepo.slettBeregningForBehandling(behandlingId)
        val updated = behandlingRepo.oppdaterBehandlingsinformasjon(
            behandlingId,
            beforeUpdate.behandlingsinformasjon().patch(behandlingsinformasjon)
        )
        // TODO fix weirdness for internal state
        val status = updated.oppdaterBehandlingsinformasjon(behandlingsinformasjon).status()
        behandlingRepo.oppdaterBehandlingStatus(behandlingId, status)
        return objectRepo.hentBehandling(behandlingId)!! // TODO just to add observers for tests and stuff until they are all gone
    }

    override fun opprettBeregning(
        behandlingId: UUID,
        fom: LocalDate,
        tom: LocalDate,
        fradrag: List<Fradrag>
    ): Behandling {
        beregningRepo.slettBeregningForBehandling(behandlingId)
        val beregnet = behandlingRepo.hentBehandling(behandlingId)!!.opprettBeregning(fom, tom, fradrag)
        beregningRepo.opprettBeregningForBehandling(behandlingId, beregnet.beregning()!!)
        behandlingRepo.oppdaterBehandlingStatus(behandlingId, beregnet.status())
        return objectRepo.hentBehandling(behandlingId)!! // TODO just to add observers for tests and stuff until they are all gone
    }

    override fun simuler(behandlingId: UUID): Either<SimuleringFeilet, Behandling> {
        val behandling = behandlingRepo.hentBehandling(behandlingId)!!
        val oppdrag = objectRepo.hentOppdrag(behandling.sakId) // TODO must use to get fnr lazy - remove when time
        val utbetaling = oppdrag.genererUtbetaling(behandling.beregning()!!)
        val utbetalingTilSimulering = NyUtbetaling(oppdrag, utbetaling, Attestant("SU"))
        return simuleringClient.simulerUtbetaling(utbetalingTilSimulering)
            .mapLeft { it }
            .map {
                behandling.utbetaling()?.let { utbetalingService.slettUtbetaling(it) }
                utbetalingService.opprettUtbetaling(oppdrag.id, utbetaling)
                utbetalingService.addSimulering(utbetaling.id, it)
                behandlingRepo.leggTilUtbetaling(behandlingId, utbetaling.id)
                val oppdatert = behandlingRepo.hentBehandling(behandlingId)!!
                oppdatert.simuler(utbetaling) // TODO just to push to correct state
                behandlingRepo.oppdaterBehandlingStatus(behandling.id, oppdatert.status())
                return objectRepo.hentBehandling(behandlingId)!!.right() // TODO dont use
            }
    }
}
