package no.nav.su.se.bakover.client

import no.nav.su.se.bakover.client.azure.AzureClient
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordelingClient
import no.nav.su.se.bakover.client.journalpost.JournalpostClientStub
import no.nav.su.se.bakover.client.kabal.KlageClientStub
import no.nav.su.se.bakover.client.kafka.KafkaPublisher
import no.nav.su.se.bakover.client.kodeverk.KodeverkHttpClient
import no.nav.su.se.bakover.client.krr.KontaktOgReservasjonsregister
import no.nav.su.se.bakover.client.pdf.PdfClient
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.skatteetaten.SkatteClient
import no.nav.su.se.bakover.client.skatteetaten.SkatteClientStub
import no.nav.su.se.bakover.client.sts.StsClient
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.client.stubs.dokarkiv.DokArkivStub
import no.nav.su.se.bakover.client.stubs.dokdistfordeling.DokDistFordelingStub
import no.nav.su.se.bakover.client.stubs.kafka.KafkaPublisherStub
import no.nav.su.se.bakover.client.stubs.krr.KontaktOgReservasjonsregisterStub
import no.nav.su.se.bakover.client.stubs.nais.LeaderPodLookupStub
import no.nav.su.se.bakover.client.stubs.oppdrag.AvstemmingStub
import no.nav.su.se.bakover.client.stubs.oppdrag.SimuleringStub
import no.nav.su.se.bakover.client.stubs.oppdrag.TilbakekrevingClientStub
import no.nav.su.se.bakover.client.stubs.oppdrag.UtbetalingStub
import no.nav.su.se.bakover.client.stubs.oppgave.OppgaveClientStub
import no.nav.su.se.bakover.client.stubs.pdf.PdfGeneratorStub
import no.nav.su.se.bakover.client.stubs.person.IdentClientStub
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.client.stubs.sts.TokenOppslagStub
import no.nav.su.se.bakover.common.SU_SE_BAKOVER_CONSUMER_ID
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.nais.LeaderPodLookup
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingClient
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import org.slf4j.LoggerFactory
import person.domain.IdentClient
import person.domain.PersonOppslag
import java.time.Clock
import java.time.LocalDate

class StubClientsBuilder(
    val clock: Clock,
    val databaseRepos: DatabaseRepos,
) : ClientsBuilder {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun build(applicationConfig: ApplicationConfig): Clients {
        return Clients(
            oauth = AzureClient(
                applicationConfig.azure.clientId,
                applicationConfig.azure.clientSecret,
                applicationConfig.azure.wellKnownUrl,
            ),
            personOppslag = PersonOppslagStub.also { log.warn("********** Using stub for ${PersonOppslag::class.java} **********") },
            tokenOppslag = if (applicationConfig.frikort.useStubForSts) {
                TokenOppslagStub.also { log.warn("********** Using stub for ${TokenOppslag::class.java} **********") }
            } else {
                StsClient(
                    applicationConfig.clientsConfig.stsUrl,
                    applicationConfig.serviceUser.username,
                    applicationConfig.serviceUser.password,
                    clock,
                )
            },
            pdfGenerator = if (applicationConfig.pdfgenLocal) {
                PdfClient("http://localhost:8081")
            } else {
                PdfGeneratorStub.also { log.warn("********** Using stub for ${PdfGenerator::class.java} **********") }
            },
            dokArkiv = DokArkivStub.also { log.warn("********** Using stub for ${DokArkiv::class.java} **********") },
            oppgaveClient = OppgaveClientStub.also { log.warn("********** Using stub for ${OppgaveClient::class.java} **********") },
            kodeverk = KodeverkHttpClient(applicationConfig.clientsConfig.kodeverkUrl, SU_SE_BAKOVER_CONSUMER_ID),
            simuleringClient = SimuleringStub(
                clock = clock,
                utbetalingerKjørtTilOgMed = { LocalDate.now(clock) },
                utbetalingRepo = databaseRepos.utbetaling,
            ).also { log.warn("********** Using stub for ${SimuleringClient::class.java} **********") },
            utbetalingPublisher = UtbetalingStub.also { log.warn("********** Using stub for ${UtbetalingPublisher::class.java} **********") },
            dokDistFordeling = DokDistFordelingStub.also { log.warn("********** Using stub for ${DokDistFordelingClient::class.java} **********") },
            avstemmingPublisher = AvstemmingStub.also { log.warn("********** Using stub for ${AvstemmingPublisher::class.java} **********") },
            identClient = IdentClientStub.also { log.warn("********** Using stub for ${IdentClient::class.java} **********") },
            kontaktOgReservasjonsregister = KontaktOgReservasjonsregisterStub.also { log.warn("********** Using stub for ${KontaktOgReservasjonsregister::class.java} **********") },
            leaderPodLookup = LeaderPodLookupStub.also { log.warn("********** Using stub for ${LeaderPodLookup::class.java} **********") },
            kafkaPublisher = KafkaPublisherStub.also { log.warn("********** Using stub for ${KafkaPublisher::class.java} **********") },
            klageClient = KlageClientStub.also { log.warn("********** Using stub for ${KlageClientStub::class.java} **********") },
            journalpostClient = JournalpostClientStub.also { log.warn("********** Using stub for ${JournalpostClientStub::class.java} **********") },
            tilbakekrevingClient = TilbakekrevingClientStub(clock).also { log.warn("********** Using stub for ${TilbakekrevingClient::class.java} **********") },
            skatteOppslag = SkatteClientStub().also { log.warn("********** Using stub for ${SkatteClient::class.java} **********") },
        )
    }
}
