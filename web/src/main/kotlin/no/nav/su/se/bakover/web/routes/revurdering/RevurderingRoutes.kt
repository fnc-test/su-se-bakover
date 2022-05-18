package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.server.routing.Route
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.web.routes.revurdering.forhåndsvarsel.forhåndsvarslingRoute
import no.nav.su.se.bakover.web.routes.sak.sakPath
import java.time.Clock

internal const val revurderingPath = "$sakPath/{sakId}/revurderinger"

internal fun Route.revurderingRoutes(
    revurderingService: RevurderingService,
    vedtakService: VedtakService,
    clock: Clock,
    satsFactory: SatsFactory,
) {
    opprettRevurderingRoute(revurderingService, satsFactory)

    oppdaterRevurderingRoute(revurderingService, satsFactory)

    beregnOgSimulerRevurdering(revurderingService, satsFactory)

    oppdaterTilbakekrevingsbehandlingRoute(revurderingService, satsFactory)

    forhåndsvarslingRoute(revurderingService, satsFactory)

    sendRevurderingTilAttestering(revurderingService, satsFactory)

    underkjennRevurdering(revurderingService, satsFactory)

    iverksettRevurderingRoute(revurderingService, satsFactory)

    brevutkastForRevurdering(revurderingService)

    leggTilGrunnlagRevurderingRoutes(revurderingService, satsFactory)

    leggTilUtlandsoppholdRoute(revurderingService, satsFactory)

    leggTilFradragRevurdering(revurderingService, clock, satsFactory)

    LeggTilBosituasjonRevurderingRoute(revurderingService, satsFactory)

    leggTilFormueRevurderingRoute(revurderingService, satsFactory)

    hentGrunnlagRevurderingRoutes(revurderingService, vedtakService, satsFactory)

    stansUtbetaling(revurderingService, satsFactory)

    gjenopptaUtbetaling(revurderingService, satsFactory)

    avsluttRevurderingRoute(revurderingService, satsFactory)
}
