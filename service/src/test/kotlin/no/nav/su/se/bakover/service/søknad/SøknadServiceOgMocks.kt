package no.nav.su.se.bakover.service.søknad

import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakFactory
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.søknad.SøknadMetrics
import no.nav.su.se.bakover.domain.søknad.SøknadRepo
import no.nav.su.se.bakover.test.defaultMock
import no.nav.su.se.bakover.test.fixedClock
import person.domain.PersonService
import java.time.Clock

/**
 * Kjører verifyNoMoreInteractions() etter runTest
 */
internal data class SøknadServiceOgMocks(
    val søknadRepo: SøknadRepo = defaultMock(),
    val sakService: SakService = defaultMock(),
    val sakFactory: SakFactory = SakFactory(clock = fixedClock),
    val pdfGenerator: PdfGenerator = defaultMock(),
    val dokArkiv: DokArkiv = defaultMock(),
    val personService: PersonService = defaultMock(),
    val oppgaveService: OppgaveService = defaultMock(),
    val søknadMetrics: SøknadMetrics = defaultMock(),
    val clock: Clock = fixedClock,
) {
    val service = SøknadServiceImpl(
        søknadRepo = søknadRepo,
        sakService = sakService,
        sakFactory = sakFactory,
        pdfGenerator = pdfGenerator,
        dokArkiv = dokArkiv,
        personService = personService,
        oppgaveService = oppgaveService,
        søknadMetrics = søknadMetrics,
        clock = fixedClock,
    )

    fun allMocks() = listOf(
        søknadRepo,
        sakService,
        pdfGenerator,
        dokArkiv,
        personService,
        oppgaveService,
        søknadMetrics,
    ).toTypedArray()

    fun verifyNoMoreInteractions() {
        org.mockito.kotlin.verifyNoMoreInteractions(
            *allMocks(),
        )
    }
}
