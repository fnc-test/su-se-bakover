package no.nav.su.se.bakover.utenlandsopphold.presentation.web.annuler

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.utenlandsopphold.presentation.web.withUtenlandsoppholdId
import no.nav.su.se.bakover.web.features.authorize

fun Route.annulerUtenlandsoppholdRoute() {
    delete("/saker/{sakId}/utenlandsopphold/{utenlandsoppholdId}") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withUtenlandsoppholdId {
                call.svar(Resultat.json(HttpStatusCode.OK, """{"utenlandsoppholdId":"$it"}"""))
            }
        }
    }
}