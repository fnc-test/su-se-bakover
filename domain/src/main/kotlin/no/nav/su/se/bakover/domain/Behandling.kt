package no.nav.su.se.bakover.domain

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Fradrag
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class Behandling(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Instant = now(),
    private var behandlingsinformasjon: Behandlingsinformasjon = Behandlingsinformasjon(
        uførhet = null,
        flyktning = null,
        lovligOpphold = null,
        fastOppholdINorge = null,
        oppholdIUtlandet = null,
        formue = null,
        personligOppmøte = null,
        sats = null
    ),
    val søknad: Søknad,
    private var beregning: Beregning? = null,
    private var utbetaling: Utbetaling? = null,
    private var status: BehandlingsStatus = BehandlingsStatus.OPPRETTET,
    private var attestant: Attestant? = null,
    val sakId: UUID
) : PersistentDomainObject<BehandlingPersistenceObserver>() {

    private var tilstand: Tilstand = resolve(status)

    fun status() = tilstand.status

    fun attestant() = attestant

    fun beregning() = beregning

    fun behandlingsinformasjon() = behandlingsinformasjon

    /**
     * Henter fødselsnummer fra sak via persisteringslaget (lazy)
     */
    val fnr: Fnr by lazy { persistenceObserver.hentFnr(sakId) }

    fun utbetaling() = utbetaling

    private fun resolve(status: BehandlingsStatus): Tilstand = when (status) {
        BehandlingsStatus.OPPRETTET -> Opprettet()
        BehandlingsStatus.VILKÅRSVURDERT_INNVILGET -> Vilkårsvurdert().Innvilget()
        BehandlingsStatus.VILKÅRSVURDERT_AVSLAG -> Vilkårsvurdert().Avslag()
        BehandlingsStatus.BEREGNET -> Beregnet()
        BehandlingsStatus.SIMULERT -> Simulert()
        BehandlingsStatus.TIL_ATTESTERING_INNVILGET -> TilAttestering().Innvilget()
        BehandlingsStatus.TIL_ATTESTERING_AVSLAG -> TilAttestering().Avslag()
        BehandlingsStatus.IVERKSATT_INNVILGET -> Iverksatt().Innvilget()
        BehandlingsStatus.IVERKSATT_AVSLAG -> Iverksatt().Avslag()
    }

    fun oppdaterBehandlingsinformasjon(oppdatert: Behandlingsinformasjon): Behandling {
        tilstand.oppdaterBehandlingsinformasjon(oppdatert)
        return this
    }

    fun opprettBeregning(
        fom: LocalDate,
        tom: LocalDate,
        sats: Sats = Sats.HØY,
        fradrag: List<Fradrag> = emptyList()
    ): Behandling {
        tilstand.opprettBeregning(fom, tom, sats, fradrag)
        return this
    }

    fun simuler(simuleringClient: SimuleringClient): Either<SimuleringFeilet, Behandling> {
        return tilstand.simuler(simuleringClient)
    }

    fun sendTilAttestering(aktørId: AktørId, oppgave: OppgaveClient): Either<KunneIkkeOppretteOppgave, Behandling> {
        return tilstand.sendTilAttestering(aktørId, oppgave)
    }

    fun iverksett(
        attestant: Attestant,
        publisher: UtbetalingPublisher
    ): Either<UtbetalingPublisher.KunneIkkeSendeUtbetaling, Behandling> {
        return tilstand.iverksett(attestant, publisher)
    }

    override fun equals(other: Any?) = other is Behandling && id == other.id
    override fun hashCode() = id.hashCode()

    interface Tilstand {
        val status: BehandlingsStatus

        fun oppdaterBehandlingsinformasjon(oppdatert: Behandlingsinformasjon) {
            throw TilstandException(status, this::oppdaterBehandlingsinformasjon.toString())
        }

        fun opprettBeregning(
            fom: LocalDate,
            tom: LocalDate,
            sats: Sats = Sats.HØY,
            fradrag: List<Fradrag>
        ) {
            throw TilstandException(status, this::opprettBeregning.toString())
        }

        fun simuler(simuleringClient: SimuleringClient): Either<SimuleringFeilet, Behandling> {
            throw TilstandException(status, this::simuler.toString())
        }

        fun sendTilAttestering(aktørId: AktørId, oppgave: OppgaveClient): Either<KunneIkkeOppretteOppgave, Behandling> {
            throw TilstandException(status, this::sendTilAttestering.toString())
        }

        fun iverksett(
            attestant: Attestant,
            publish: UtbetalingPublisher
        ): Either<UtbetalingPublisher.KunneIkkeSendeUtbetaling, Behandling> {
            throw TilstandException(status, this::iverksett.toString())
        }
    }

    private fun nyTilstand(target: Tilstand): Tilstand {
        status = persistenceObserver.oppdaterBehandlingStatus(id, target.status)
        tilstand = resolve(status)
        return tilstand
    }

    private inner class Opprettet : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.OPPRETTET

        override fun oppdaterBehandlingsinformasjon(oppdatert: Behandlingsinformasjon) {
            behandlingsinformasjon = persistenceObserver.oppdaterBehandlingsinformasjon(this@Behandling.id, behandlingsinformasjon.patch(oppdatert))
            if (behandlingsinformasjon.isInnvilget()) {
                nyTilstand(Vilkårsvurdert().Innvilget())
            } else if (behandlingsinformasjon.isAvslag()) {
                nyTilstand(Vilkårsvurdert().Avslag())
            }
        }
    }

    private open inner class Vilkårsvurdert : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.VILKÅRSVURDERT_INNVILGET

        override fun oppdaterBehandlingsinformasjon(oppdatert: Behandlingsinformasjon) {
            nyTilstand(Opprettet()).oppdaterBehandlingsinformasjon(oppdatert)
        }

        inner class Innvilget : Vilkårsvurdert() {
            override fun opprettBeregning(fom: LocalDate, tom: LocalDate, sats: Sats, fradrag: List<Fradrag>) {
                this@Behandling.beregning = persistenceObserver.opprettBeregning(
                    behandlingId = id,
                    beregning = Beregning(
                        fom = fom,
                        tom = tom,
                        sats = sats,
                        fradrag = fradrag
                    )
                )
                nyTilstand(Beregnet())
            }
        }

        inner class Avslag : Vilkårsvurdert() {
            override val status: BehandlingsStatus = BehandlingsStatus.VILKÅRSVURDERT_AVSLAG

            override fun sendTilAttestering(
                aktørId: AktørId,
                oppgave: OppgaveClient
            ): Either<KunneIkkeOppretteOppgave, Behandling> = oppgave.opprettOppgave(
                OppgaveConfig.Attestering(
                    sakId = sakId.toString(),
                    aktørId = aktørId
                )
            ).map {
                nyTilstand(TilAttestering().Avslag())
                this@Behandling
            }
        }
    }

    private inner class Beregnet : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.BEREGNET

        override fun opprettBeregning(fom: LocalDate, tom: LocalDate, sats: Sats, fradrag: List<Fradrag>) {
            nyTilstand(Vilkårsvurdert()).opprettBeregning(fom, tom, sats, fradrag)
        }

        override fun oppdaterBehandlingsinformasjon(oppdatert: Behandlingsinformasjon) {
            nyTilstand(Opprettet()).oppdaterBehandlingsinformasjon(oppdatert)
        }

        override fun simuler(simuleringClient: SimuleringClient): Either<SimuleringFeilet, Behandling> {
            val oppdrag = persistenceObserver.hentOppdrag(sakId)
            val utbetalingTilSimulering = oppdrag.generererUtbetaling(id, beregning!!.hentPerioder())
            return simuleringClient.simulerUtbetaling(
                NyUtbetaling(
                    oppdrag = oppdrag,
                    utbetaling = utbetalingTilSimulering,
                    oppdragGjelder = fnr,
                    attestant = Attestant("SU") // TODO: Vi har ikke noe konsept om saksbehandlerid enda.
                )
            ).map { simulering ->
                this@Behandling.utbetaling = oppdrag.opprettUtbetaling(utbetalingTilSimulering).also {
                    it.addSimulering(simulering)
                }
                nyTilstand(Simulert())
                this@Behandling
            }
        }
    }

    private inner class Simulert : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.SIMULERT

        override fun sendTilAttestering(
            aktørId: AktørId,
            oppgave: OppgaveClient
        ): Either<KunneIkkeOppretteOppgave, Behandling> = oppgave.opprettOppgave(
            OppgaveConfig.Attestering(
                sakId = sakId.toString(),
                aktørId = aktørId
            )
        ).map {
            nyTilstand(TilAttestering().Innvilget())
            this@Behandling
        }

        override fun opprettBeregning(fom: LocalDate, tom: LocalDate, sats: Sats, fradrag: List<Fradrag>) {
            nyTilstand(Vilkårsvurdert().Innvilget()).opprettBeregning(fom, tom, sats, fradrag)
        }

        override fun oppdaterBehandlingsinformasjon(oppdatert: Behandlingsinformasjon) {
            nyTilstand(Opprettet()).oppdaterBehandlingsinformasjon(oppdatert)
        }

        override fun simuler(simuleringClient: SimuleringClient): Either<SimuleringFeilet, Behandling> {
            return nyTilstand(Beregnet()).simuler(simuleringClient)
        }
    }

    private open inner class TilAttestering : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.TIL_ATTESTERING_INNVILGET

        inner class Innvilget : TilAttestering() {
            override fun iverksett(
                attestant: Attestant,
                publish: UtbetalingPublisher
            ): Either<UtbetalingPublisher.KunneIkkeSendeUtbetaling, Behandling> {
                this@Behandling.attestant = persistenceObserver.attester(id, attestant)
                return publish.publish(
                    NyUtbetaling(
                        oppdrag = persistenceObserver.hentOppdrag(sakId),
                        utbetaling = utbetaling!!,
                        oppdragGjelder = fnr,
                        attestant = attestant
                    )
                ).mapLeft {
                    utbetaling!!.addOppdragsmelding(
                        Oppdragsmelding(
                            Oppdragsmelding.Oppdragsmeldingstatus.FEIL,
                            it.originalMelding
                        )
                    )
                    it
                }.map {
                    utbetaling!!.addOppdragsmelding(
                        Oppdragsmelding(
                            Oppdragsmelding.Oppdragsmeldingstatus.SENDT,
                            it
                        )
                    )
                    nyTilstand(Iverksatt().Innvilget())
                    this@Behandling
                }
            }
        }

        inner class Avslag : TilAttestering() {
            override val status: BehandlingsStatus = BehandlingsStatus.TIL_ATTESTERING_AVSLAG
            override fun iverksett(
                attestant: Attestant,
                publish: UtbetalingPublisher
            ): Either<UtbetalingPublisher.KunneIkkeSendeUtbetaling, Behandling> {
                this@Behandling.attestant = persistenceObserver.attester(id, attestant)
                nyTilstand(Iverksatt().Avslag())
                return this@Behandling.right()
            }
        }
    }

    private open inner class Iverksatt : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.IVERKSATT_INNVILGET

        inner class Innvilget : Iverksatt()
        inner class Avslag : Iverksatt() {
            override val status: BehandlingsStatus = BehandlingsStatus.IVERKSATT_AVSLAG
        }
    }

    enum class BehandlingsStatus {
        OPPRETTET,
        VILKÅRSVURDERT_INNVILGET,
        VILKÅRSVURDERT_AVSLAG,
        BEREGNET,
        SIMULERT,
        TIL_ATTESTERING_INNVILGET,
        TIL_ATTESTERING_AVSLAG,
        IVERKSATT_INNVILGET,
        IVERKSATT_AVSLAG
    }

    class TilstandException(
        val state: BehandlingsStatus,
        val operation: String,
        val msg: String = "Illegal operation: $operation for state: $state"
    ) :
        RuntimeException(msg)
}

interface BehandlingPersistenceObserver : PersistenceObserver {
    fun opprettBeregning(behandlingId: UUID, beregning: Beregning): Beregning
    fun oppdaterBehandlingStatus(
        behandlingId: UUID,
        status: Behandling.BehandlingsStatus
    ): Behandling.BehandlingsStatus
    fun oppdaterBehandlingsinformasjon(
        behandlingId: UUID,
        behandlingsinformasjon: Behandlingsinformasjon
    ): Behandlingsinformasjon

    fun hentOppdrag(sakId: UUID): Oppdrag
    fun hentFnr(sakId: UUID): Fnr
    fun attester(behandlingId: UUID, attestant: Attestant): Attestant
}
