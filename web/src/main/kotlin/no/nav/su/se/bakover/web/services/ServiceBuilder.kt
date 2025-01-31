package no.nav.su.se.bakover.web.services

import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.database.jobcontext.JobContextPostgresRepo
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.sak.SakFactory
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknad.SøknadMetrics
import no.nav.su.se.bakover.kontrollsamtale.infrastructure.setup.KontrollsamtaleSetup
import no.nav.su.se.bakover.service.SendPåminnelserOmNyStønadsperiodeServiceImpl
import no.nav.su.se.bakover.service.avstemming.AvstemmingServiceImpl
import no.nav.su.se.bakover.service.brev.BrevServiceImpl
import no.nav.su.se.bakover.service.klage.KlageServiceImpl
import no.nav.su.se.bakover.service.klage.KlageinstanshendelseServiceImpl
import no.nav.su.se.bakover.service.nøkkeltall.NøkkeltallServiceImpl
import no.nav.su.se.bakover.service.oppgave.OppgaveServiceImpl
import no.nav.su.se.bakover.service.person.PersonServiceImpl
import no.nav.su.se.bakover.service.regulering.ReguleringServiceImpl
import no.nav.su.se.bakover.service.revurdering.GjenopptaYtelseServiceImpl
import no.nav.su.se.bakover.service.revurdering.RevurderingServiceImpl
import no.nav.su.se.bakover.service.revurdering.StansYtelseServiceImpl
import no.nav.su.se.bakover.service.sak.SakServiceImpl
import no.nav.su.se.bakover.service.skatt.JournalførSkattDokumentService
import no.nav.su.se.bakover.service.skatt.SkattDokumentServiceImpl
import no.nav.su.se.bakover.service.skatt.SkatteServiceImpl
import no.nav.su.se.bakover.service.statistikk.ResendStatistikkhendelserServiceImpl
import no.nav.su.se.bakover.service.søknad.AvslåSøknadManglendeDokumentasjonServiceImpl
import no.nav.su.se.bakover.service.søknad.SøknadServiceImpl
import no.nav.su.se.bakover.service.søknad.lukk.LukkSøknadServiceImpl
import no.nav.su.se.bakover.service.søknadsbehandling.IverksettSøknadsbehandlingServiceImpl
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServiceImpl
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServices
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingServiceImpl
import no.nav.su.se.bakover.service.utbetaling.UtbetalingServiceImpl
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakServiceImpl
import no.nav.su.se.bakover.service.vedtak.VedtakServiceImpl
import no.nav.su.se.bakover.statistikk.StatistikkEventObserverBuilder
import java.time.Clock

data object ServiceBuilder {
    fun build(
        databaseRepos: DatabaseRepos,
        clients: Clients,
        behandlingMetrics: BehandlingMetrics,
        søknadMetrics: SøknadMetrics,
        clock: Clock,
        satsFactory: SatsFactory,
        applicationConfig: ApplicationConfig,
        dbMetrics: DbMetrics,
    ): Services {
        val personService = PersonServiceImpl(clients.personOppslag)

        val statistikkEventObserver = StatistikkEventObserverBuilder(
            kafkaPublisher = clients.kafkaPublisher,
            personService = personService,
            clock = clock,
            gitCommit = applicationConfig.gitCommit,
        ).statistikkService
        val utbetalingService = UtbetalingServiceImpl(
            utbetalingRepo = databaseRepos.utbetaling,
            simuleringClient = clients.simuleringClient,
            utbetalingPublisher = clients.utbetalingPublisher,
        )
        val brevService = BrevServiceImpl(
            pdfGenerator = clients.pdfGenerator,
            dokumentRepo = databaseRepos.dokumentRepo,
            personService = personService,
            sessionFactory = databaseRepos.sessionFactory,
            identClient = clients.identClient,
            clock = clock,
        )
        val sakService = SakServiceImpl(
            sakRepo = databaseRepos.sak,
            clock = clock,
            dokumentRepo = databaseRepos.dokumentRepo,
            brevService = brevService,
            clients.journalpostClient,
            personService = personService,
        ).apply { addObserver(statistikkEventObserver) }

        val oppgaveService = OppgaveServiceImpl(
            oppgaveClient = clients.oppgaveClient,
        )
        val søknadService = SøknadServiceImpl(
            søknadRepo = databaseRepos.søknad,
            sakService = sakService,
            sakFactory = SakFactory(clock = clock),
            pdfGenerator = clients.pdfGenerator,
            dokArkiv = clients.dokArkiv,
            personService = personService,
            oppgaveService = oppgaveService,
            søknadMetrics = søknadMetrics,
            clock = clock,
        ).apply {
            addObserver(statistikkEventObserver)
        }
        val ferdigstillVedtakService = FerdigstillVedtakServiceImpl(
            brevService = brevService,
            oppgaveService = oppgaveService,
            vedtakRepo = databaseRepos.vedtakRepo,
            behandlingMetrics = behandlingMetrics,
            clock = clock,
            satsFactory = satsFactory,
        )

        val vedtakService = VedtakServiceImpl(
            vedtakRepo = databaseRepos.vedtakRepo,
        )

        val tilbakekrevingService = TilbakekrevingServiceImpl(
            tilbakekrevingRepo = databaseRepos.tilbakekrevingRepo,
            tilbakekrevingClient = clients.tilbakekrevingClient,
            vedtakService = vedtakService,
            brevService = brevService,
            sessionFactory = databaseRepos.sessionFactory,
            clock = clock,
            satsFactory = satsFactory,
        )

        val skattDokumentService = SkattDokumentServiceImpl(
            pdfGenerator = clients.pdfGenerator,
            personOppslag = clients.personOppslag,
            dokumentSkattRepo = databaseRepos.dokumentSkattRepo,
            journalførSkattDokumentService = JournalførSkattDokumentService(
                dokArkiv = clients.dokArkiv,
                sakService = sakService,
                dokumentSkattRepo = databaseRepos.dokumentSkattRepo,
            ),
            clock = clock,
        )

        val skatteServiceImpl = SkatteServiceImpl(
            skatteClient = clients.skatteOppslag,
            skattDokumentService = skattDokumentService,
            clock = clock,
        )

        val stansAvYtelseService = StansYtelseServiceImpl(
            utbetalingService = utbetalingService,
            revurderingRepo = databaseRepos.revurderingRepo,
            vedtakService = vedtakService,
            sakService = sakService,
            clock = clock,
            sessionFactory = databaseRepos.sessionFactory,
        ).apply { addObserver(statistikkEventObserver) }

        val kontrollsamtaleSetup = KontrollsamtaleSetup.create(
            sakService = sakService,
            personService = personService,
            brevService = brevService,
            oppgaveService = oppgaveService,
            sessionFactory = databaseRepos.sessionFactory as PostgresSessionFactory,
            dbMetrics = dbMetrics,
            clock = clock,
            serviceUser = applicationConfig.serviceUser.username,
            jobContextPostgresRepo = JobContextPostgresRepo(
                // TODO jah: Finnes nå 2 instanser av denne. Opprettes også i DatabaseBuilder for StønadsperiodePostgresRepo
                sessionFactory = databaseRepos.sessionFactory as PostgresSessionFactory,
            ),
            journalpostClient = clients.journalpostClient,
            stansAvYtelseService = stansAvYtelseService,
        )

        val revurderingService = RevurderingServiceImpl(
            utbetalingService = utbetalingService,
            revurderingRepo = databaseRepos.revurderingRepo,
            oppgaveService = oppgaveService,
            personService = personService,
            brevService = brevService,
            clock = clock,
            vedtakRepo = databaseRepos.vedtakRepo,
            sessionFactory = databaseRepos.sessionFactory,
            formuegrenserFactory = satsFactory.formuegrenserFactory,
            sakService = sakService,
            tilbakekrevingService = tilbakekrevingService,
            satsFactory = satsFactory,
            annullerKontrollsamtaleService = kontrollsamtaleSetup.annullerKontrollsamtaleService,
            ferdigstillVedtakService = ferdigstillVedtakService,
        ).apply { addObserver(statistikkEventObserver) }

        val gjenopptakAvYtelseService = GjenopptaYtelseServiceImpl(
            utbetalingService = utbetalingService,
            revurderingRepo = databaseRepos.revurderingRepo,
            clock = clock,
            vedtakRepo = databaseRepos.vedtakRepo,
            sakService = sakService,
            sessionFactory = databaseRepos.sessionFactory,
        ).apply { addObserver(statistikkEventObserver) }

        val reguleringService = ReguleringServiceImpl(
            reguleringRepo = databaseRepos.reguleringRepo,
            sakService = sakService,
            utbetalingService = utbetalingService,
            vedtakService = vedtakService,
            sessionFactory = databaseRepos.sessionFactory,
            clock = clock,
            tilbakekrevingService = tilbakekrevingService,
            satsFactory = satsFactory,
        ).apply { addObserver(statistikkEventObserver) }

        val nøkkelTallService = NøkkeltallServiceImpl(databaseRepos.nøkkeltallRepo)

        val søknadsbehandlingService = SøknadsbehandlingServiceImpl(
            søknadsbehandlingRepo = databaseRepos.søknadsbehandling,
            utbetalingService = utbetalingService,
            personService = personService,
            oppgaveService = oppgaveService,
            behandlingMetrics = behandlingMetrics,
            brevService = brevService,
            clock = clock,
            sakService = sakService,
            formuegrenserFactory = satsFactory.formuegrenserFactory,
            satsFactory = satsFactory,
            sessionFactory = databaseRepos.sessionFactory,
            skatteService = skatteServiceImpl,
        ).apply {
            addObserver(statistikkEventObserver)
        }
        val klageService = KlageServiceImpl(
            sakService = sakService,
            klageRepo = databaseRepos.klageRepo,
            vedtakService = vedtakService,
            brevService = brevService,
            personService = personService,
            klageClient = clients.klageClient,
            sessionFactory = databaseRepos.sessionFactory,
            oppgaveService = oppgaveService,
            journalpostClient = clients.journalpostClient,
            clock = clock,
        ).apply { addObserver(statistikkEventObserver) }
        val klageinstanshendelseService = KlageinstanshendelseServiceImpl(
            klageinstanshendelseRepo = databaseRepos.klageinstanshendelseRepo,
            klageRepo = databaseRepos.klageRepo,
            oppgaveService = oppgaveService,
            personService = personService,
            sessionFactory = databaseRepos.sessionFactory,
            clock = clock,
        )
        val iverksettSøknadsbehandlingService = IverksettSøknadsbehandlingServiceImpl(
            sakService = sakService,
            clock = clock,
            utbetalingService = utbetalingService,
            sessionFactory = databaseRepos.sessionFactory,
            søknadsbehandlingRepo = databaseRepos.søknadsbehandling,
            vedtakRepo = databaseRepos.vedtakRepo,
            opprettPlanlagtKontrollsamtaleService = kontrollsamtaleSetup.opprettPlanlagtKontrollsamtaleService,
            ferdigstillVedtakService = ferdigstillVedtakService,
            brevService = brevService,
            skattDokumentService = skattDokumentService,
            satsFactory = satsFactory,
        ).apply { addObserver(statistikkEventObserver) }
        return Services(
            avstemming = AvstemmingServiceImpl(
                repo = databaseRepos.avstemming,
                publisher = clients.avstemmingPublisher,
                clock = clock,
            ),
            utbetaling = utbetalingService,
            sak = sakService,
            søknad = søknadService,
            brev = brevService,
            lukkSøknad = LukkSøknadServiceImpl(
                søknadService = søknadService,
                brevService = brevService,
                oppgaveService = oppgaveService,
                søknadsbehandlingService = søknadsbehandlingService,
                sakService = sakService,
                sessionFactory = databaseRepos.sessionFactory,
            ).apply {
                addObserver(statistikkEventObserver)
            },
            oppgave = oppgaveService,
            person = personService,
            søknadsbehandling = SøknadsbehandlingServices(
                søknadsbehandlingService = søknadsbehandlingService,
                iverksettSøknadsbehandlingService = iverksettSøknadsbehandlingService,
            ),
            ferdigstillVedtak = ferdigstillVedtakService,
            revurdering = revurderingService,
            vedtakService = vedtakService,
            nøkkeltallService = nøkkelTallService,
            avslåSøknadManglendeDokumentasjonService = AvslåSøknadManglendeDokumentasjonServiceImpl(
                clock = clock,
                sakService = sakService,
                satsFactory = satsFactory,
                iverksettSøknadsbehandlingService = iverksettSøknadsbehandlingService,
                utbetalingService = utbetalingService,
                brevService = brevService,
            ),
            klageService = klageService,
            klageinstanshendelseService = klageinstanshendelseService,
            reguleringService = reguleringService,
            tilbakekrevingService = tilbakekrevingService,
            sendPåminnelserOmNyStønadsperiodeService = SendPåminnelserOmNyStønadsperiodeServiceImpl(
                clock = clock,
                sakRepo = databaseRepos.sak,
                sessionFactory = databaseRepos.sessionFactory,
                brevService = brevService,
                sendPåminnelseNyStønadsperiodeJobRepo = databaseRepos.sendPåminnelseNyStønadsperiodeJobRepo,
                formuegrenserFactory = satsFactory.formuegrenserFactory,
            ),
            skatteService = skatteServiceImpl,
            stansYtelse = stansAvYtelseService,
            gjenopptaYtelse = gjenopptakAvYtelseService,
            kontrollsamtaleSetup = kontrollsamtaleSetup,
            resendStatistikkhendelserService = ResendStatistikkhendelserServiceImpl(
                vedtakRepo = databaseRepos.vedtakRepo,
                sakRepo = databaseRepos.sak,
                statistikkEventObserver = statistikkEventObserver,
            ),
        )
    }
}
