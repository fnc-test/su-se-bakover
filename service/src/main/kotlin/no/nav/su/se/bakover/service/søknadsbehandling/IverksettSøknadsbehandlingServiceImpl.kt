package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksattSøknadsbehandlingResponse
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettSøknadsbehandlingCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettSøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.KunneIkkeIverksetteSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.OpprettKontrollsamtaleVedNyStønadsperiodeService
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.iverksettSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Stønadsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.service.skatt.SkattDokumentService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import java.time.Clock

class IverksettSøknadsbehandlingServiceImpl(
    private val sakService: SakService,
    private val clock: Clock,
    private val utbetalingService: UtbetalingService,
    private val sessionFactory: SessionFactory,
    private val søknadsbehandlingRepo: SøknadsbehandlingRepo,
    private val vedtakRepo: VedtakRepo,
    private val opprettPlanlagtKontrollsamtaleService: OpprettKontrollsamtaleVedNyStønadsperiodeService,
    private val ferdigstillVedtakService: FerdigstillVedtakService,
    private val brevService: BrevService,
    private val skattDokumentService: SkattDokumentService,
    private val satsFactory: SatsFactory,
) : IverksettSøknadsbehandlingService {

    private val observers: MutableList<StatistikkEventObserver> = mutableListOf()

    fun addObserver(observer: StatistikkEventObserver) {
        observers.add(observer)
    }

    override fun iverksett(
        command: IverksettSøknadsbehandlingCommand,
    ): Either<KunneIkkeIverksetteSøknadsbehandling, Triple<Sak, IverksattSøknadsbehandling, Stønadsvedtak>> {
        return sakService.hentSakForSøknadsbehandling(command.behandlingId)
            .iverksettSøknadsbehandling(
                command = command,
                genererPdf = brevService::lagDokument,
                clock = clock,
                simulerUtbetaling = utbetalingService::simulerUtbetaling,
                satsFactory = satsFactory,
            )
            .map {
                iverksett(it)
                Triple(it.sak, it.søknadsbehandling, it.vedtak)
            }
    }

    override fun iverksett(
        iverksattSøknadsbehandlingResponse: IverksattSøknadsbehandlingResponse<*>,
    ) {
        iverksattSøknadsbehandlingResponse.ferdigstillIverksettelseITransaksjon(
            klargjørUtbetaling = utbetalingService::klargjørUtbetaling,
            sessionFactory = sessionFactory,
            lagreSøknadsbehandling = søknadsbehandlingRepo::lagre,
            lagreVedtak = vedtakRepo::lagreITransaksjon,
            statistikkObservers = observers,
            opprettPlanlagtKontrollsamtale = opprettPlanlagtKontrollsamtaleService::opprett,
            lagreDokument = brevService::lagreDokument,
            lukkOppgave = ferdigstillVedtakService::lukkOppgaveMedBruker,
        ) { vedtak, tx -> skattDokumentService.genererOgLagre(vedtak, tx) }
    }
}
