package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.getOrHandle
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.audit.AuditLogEvent
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.vilkår.LeggTilFastOppholdINorgeRequest
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.vilkår.fastopphold.LeggTilVurderingsperiodeFastOppholdJson
import no.nav.su.se.bakover.web.routes.vilkår.fastopphold.tilResultat
import no.nav.su.se.bakover.web.routes.vilkår.fastopphold.toDomain

internal fun Route.fastOppholdVilkårRoutes(
    revurderingService: RevurderingService,
    satsFactory: SatsFactory,
) {
    post("$revurderingPath/{revurderingId}/fastopphold") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withRevurderingId {
                call.withBody<List<LeggTilVurderingsperiodeFastOppholdJson>> { body ->
                    call.svar(
                        revurderingService.leggTilFastOppholdINorgeVilkår(
                            request = LeggTilFastOppholdINorgeRequest(
                                behandlingId = it,
                                vilkår = body.toDomain().getOrHandle { return@withBody call.svar(it.tilResultat()) },
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
