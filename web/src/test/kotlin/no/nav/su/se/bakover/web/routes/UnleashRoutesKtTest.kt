package no.nav.su.se.bakover.web.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.http.HttpMethod.Companion.Get
import io.ktor.server.server.testing.handleRequest
import io.ktor.server.server.testing.withTestApplication
import no.nav.su.se.bakover.service.toggles.ToggleService
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert

internal class UnleashRoutesKtTest {

    @Test
    fun unleashRoutes() {
        val toggleMock = mock<ToggleService> {
            on { isEnabled("supstonad.enToggle") } doReturn true
            on { isEnabled("supstonad.annenToggle") } doReturn false
        }
        testApplication(
            {
                testSusebakover(
                    services = TestServicesBuilder.services().copy(toggles = toggleMock)
                )
            }
        ) {
            handleRequest(Get, "/toggles/supstonad.enToggle").apply {
                assertEquals(HttpStatusCode.OK, status)
                JSONAssert.assertEquals("""{"supstonad.enToggle": true}""".trimIndent(), response.content!!, true)
            }
        }
    }
}
