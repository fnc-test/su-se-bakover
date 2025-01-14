package no.nav.su.se.bakover.domain.revurdering.attestering

import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.RevurderingsutfallSomIkkeStøttes
import java.util.UUID
import kotlin.reflect.KClass

sealed class KunneIkkeSendeRevurderingTilAttestering {
    data class FeilInnvilget(val feil: SimulertRevurdering.KunneIkkeSendeInnvilgetRevurderingTilAttestering) :
        KunneIkkeSendeRevurderingTilAttestering()

    data class FeilOpphørt(val feil: SimulertRevurdering.Opphørt.KanIkkeSendeOpphørtRevurderingTilAttestering) :
        KunneIkkeSendeRevurderingTilAttestering()

    data object FantIkkeRevurdering : KunneIkkeSendeRevurderingTilAttestering()
    data object FantIkkeAktørId : KunneIkkeSendeRevurderingTilAttestering()
    data object KunneIkkeOppretteOppgave : KunneIkkeSendeRevurderingTilAttestering()
    data object KanIkkeRegulereGrunnbeløpTilOpphør : KunneIkkeSendeRevurderingTilAttestering()
    data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>) :
        KunneIkkeSendeRevurderingTilAttestering()

    data object FeilutbetalingStøttesIkke : KunneIkkeSendeRevurderingTilAttestering()
    data class RevurderingsutfallStøttesIkke(val feilmeldinger: List<RevurderingsutfallSomIkkeStøttes>) :
        KunneIkkeSendeRevurderingTilAttestering()

    data class SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving(
        val revurderingId: UUID,
    ) : KunneIkkeSendeRevurderingTilAttestering()
}
