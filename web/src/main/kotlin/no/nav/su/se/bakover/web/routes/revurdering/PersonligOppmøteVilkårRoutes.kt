package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.vilkår.oppmøte.LeggTilPersonligOppmøteVilkårRequest
import no.nav.su.se.bakover.web.routes.vilkår.LeggTilVurderingsperiodePersonligOppmøteJson
import no.nav.su.se.bakover.web.routes.vilkår.tilResultat
import no.nav.su.se.bakover.web.routes.vilkår.toDomain
import java.time.Clock

internal fun Route.personligOppmøteVilkårRoutes(
    revurderingService: RevurderingService,
    satsFactory: SatsFactory,
    clock: Clock,
) {
    post("$REVURDERING_PATH/{revurderingId}/personligoppmøte") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withRevurderingId {
                call.withBody<List<LeggTilVurderingsperiodePersonligOppmøteJson>> { body ->
                    call.svar(
                        revurderingService.leggTilPersonligOppmøteVilkår(
                            request = LeggTilPersonligOppmøteVilkårRequest(
                                behandlingId = it,
                                vilkår = body.toDomain(clock),
                            ),
                        ).fold(
                            { it.tilResultat() },
                            {
                                call.audit(it.revurdering.fnr, AuditLogEvent.Action.UPDATE, it.revurdering.id)
                                Resultat.json(HttpStatusCode.Created, it.json(satsFactory))
                            },
                        ),
                    )
                }
            }
        }
    }
}
