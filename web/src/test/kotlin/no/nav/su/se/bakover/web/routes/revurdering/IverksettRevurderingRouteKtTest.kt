package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteRevurdering
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.behandling.TestBeregning
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.periode
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.requestPath
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.testServices
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.vedtak
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.LocalDate
import java.util.UUID

internal class IverksettRevurderingRouteKtTest {

    private val revurderingId = UUID.randomUUID()

    @Test
    fun `uautoriserte kan ikke iverksette revurdering`() {
        withTestApplication({
            testSusebakover()
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/$revurderingId/iverksett",
                listOf(Brukerrolle.Veileder)
            ).apply {
                response.status() shouldBe HttpStatusCode.Forbidden
                JSONAssert.assertEquals(
                    """
                    {
                        "message":"Bruker mangler en av de tillatte rollene: Attestant."
                    }
                    """.trimIndent(),
                    response.content,
                    true
                )
            }
        }
    }

    @Test
    fun `iverksett revurdering`() {
        val iverksattRevurdering = IverksattRevurdering(
            id = UUID.randomUUID(),
            periode = periode,
            opprettet = Tidspunkt.now(),
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = ""),
            beregning = TestBeregning,
            simulering = Simulering(
                gjelderId = vedtak.behandling.fnr,
                gjelderNavn = "Test",
                datoBeregnet = LocalDate.now(),
                nettoBeløp = 0,
                periodeList = listOf()
            ),
            oppgaveId = OppgaveId("OppgaveId"),
            attestant = NavIdentBruker.Attestant("attestant"),
            utbetalingId = UUID30.randomUUID(),
            eksterneIverksettingsteg = JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert
        )

        val revurderingServiceMock = mock<RevurderingService> {
            on { iverksett(any(), any()) } doReturn iverksattRevurdering.right()
        }

        withTestApplication({
            testSusebakover(services = testServices.copy(revurdering = revurderingServiceMock))
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/${iverksattRevurdering.id}/iverksett",
                listOf(Brukerrolle.Attestant)
            ).apply {
                response.status() shouldBe HttpStatusCode.OK
                val actualResponse = objectMapper.readValue<TilAttesteringJson>(response.content!!)
                actualResponse.id shouldBe iverksattRevurdering.id.toString()
                actualResponse.status shouldBe RevurderingsStatus.TIL_ATTESTERING
            }
        }
    }

    @Test
    fun `fant ikke revurdering`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeIverksetteRevurdering.FantIkkeRevurdering,
            expectedStatusCode = HttpStatusCode.NotFound,
            expectedJsonResponse = """
                {
                    "message":"Fant ikke revurdering",
                    "code":"fant_ikke_revurdering"
                }
            """.trimIndent()
        )
    }

    @Test
    fun `ugyldig tilstand`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeIverksetteRevurdering.UgyldigTilstand(
                fra = IverksattRevurdering::class,
                til = OpprettetRevurdering::class,
            ),
            expectedStatusCode = HttpStatusCode.BadRequest,
            expectedJsonResponse = """
                {
                    "message":"Kan ikke gå fra tilstanden IverksattRevurdering til tilstanden OpprettetRevurdering",
                    "code":"ugyldig_periode"
                }
            """.trimIndent()
        )
    }

    @Test
    fun `attestant og saksbehandler kan ikke være samme person`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson,
            expectedStatusCode = HttpStatusCode.Forbidden,
            expectedJsonResponse = """
                {
                    "message":"Attestant og saksbehandler kan ikke være samme person",
                    "code":"attestant_og_saksbehandler_kan_ikke_være_samme_person"
                }
            """.trimIndent()
        )
    }

    @Test
    fun `kunne ikke journalføre brev`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeIverksetteRevurdering.KunneIkkeJournalføreBrev,
            expectedStatusCode = HttpStatusCode.InternalServerError,
            expectedJsonResponse = """
                {
                    "message":"Feil ved journalføring av vedtaksbrev",
                    "code":"kunne_ikke_journalføre_brev"
                }
            """.trimIndent()
        )
    }

    @Test
    fun `kunne ikke kontrollsimulere`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeIverksetteRevurdering.KunneIkkeKontrollsimulere,
            expectedStatusCode = HttpStatusCode.InternalServerError,
            expectedJsonResponse = """
                {
                    "message":"Kunne ikke utføre kontrollsimulering",
                    "code":"kunne_ikke_kontrollsimulere"
                }
            """.trimIndent()
        )
    }

    @Test
    fun `kunne ikke utbetale`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale,
            expectedStatusCode = HttpStatusCode.InternalServerError,
            expectedJsonResponse = """
                {
                    "message":"Kunne ikke utføre utbetaling",
                    "code":"kunne_ikke_utbetale"
                }
            """.trimIndent()
        )
    }

    @Test
    fun `simulering har blitt endret siden saksbehandler simulerte`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeIverksetteRevurdering.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte,
            expectedStatusCode = HttpStatusCode.InternalServerError,
            expectedJsonResponse = """
                {
                    "message":"Oppdaget inkonsistens mellom tidligere utført simulering og kontrollsimulering. Ny simulering må utføres og kontrolleres før iverksetting kan gjennomføres",
                    "code":"simulering_har_blitt_endret_siden_saksbehandler_simulerte"
                }
            """.trimIndent()
        )
    }

    private fun shouldMapErrorCorrectly(
        error: KunneIkkeIverksetteRevurdering,
        expectedStatusCode: HttpStatusCode,
        expectedJsonResponse: String
    ) {
        val revurderingServiceMock = mock<RevurderingService> {
            on { iverksett(any(), any()) } doReturn error.left()
        }

        withTestApplication({
            testSusebakover(services = testServices.copy(revurdering = revurderingServiceMock))
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/$revurderingId/iverksett",
                listOf(Brukerrolle.Attestant)
            ).apply {
                response.status() shouldBe expectedStatusCode
                JSONAssert.assertEquals(
                    expectedJsonResponse,
                    response.content,
                    true
                )
            }
        }
    }
}