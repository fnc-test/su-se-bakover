package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.flatMap
import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.web.routes.grunnlag.LeggTilBosituasjonJsonRequest
import no.nav.su.se.bakover.web.routes.grunnlag.tilResultat

internal fun Route.leggTilGrunnlagBosituasjonRoutes(
    revurderingService: RevurderingService,
    satsFactory: SatsFactory,
) {
    post("$REVURDERING_PATH/{revurderingId}/bosituasjongrunnlag") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withRevurderingId { revurderingId ->
                    call.withBody<LeggTilBosituasjonJsonRequest> { json ->
                        call.svar(
                            json.toService(revurderingId)
                                .mapLeft { it }
                                .flatMap {
                                    revurderingService.leggTilBosituasjongrunnlag(it)
                                        .mapLeft { it.tilResultat() }
                                        .map { response ->
                                            call.audit(
                                                response.revurdering.fnr,
                                                AuditLogEvent.Action.UPDATE,
                                                response.revurdering.id,
                                            )
                                            call.sikkerlogg("Lagret bosituasjon for revudering $revurderingId på $sakId")
                                            Resultat.json(HttpStatusCode.OK, serialize(response.toJson(satsFactory)))
                                        }
                                }.getOrElse { it },
                        )
                    }
                }
            }
        }
    }
}
