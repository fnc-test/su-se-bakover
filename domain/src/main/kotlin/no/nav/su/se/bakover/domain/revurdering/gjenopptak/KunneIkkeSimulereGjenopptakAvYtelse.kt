package no.nav.su.se.bakover.domain.revurdering.gjenopptak

import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerGjenopptakFeil
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import kotlin.reflect.KClass

sealed interface KunneIkkeSimulereGjenopptakAvYtelse {
    object FantIkkeSak : KunneIkkeSimulereGjenopptakAvYtelse
    object FantIkkeRevurdering : KunneIkkeSimulereGjenopptakAvYtelse
    object FantIngenVedtak : KunneIkkeSimulereGjenopptakAvYtelse
    object FinnesÅpenGjenopptaksbehandling : KunneIkkeSimulereGjenopptakAvYtelse
    data class KunneIkkeSimulere(val feil: SimulerGjenopptakFeil) : KunneIkkeSimulereGjenopptakAvYtelse
    object KunneIkkeOppretteRevurdering : KunneIkkeSimulereGjenopptakAvYtelse
    data class UgyldigTypeForOppdatering(val type: KClass<out AbstraktRevurdering>) : KunneIkkeSimulereGjenopptakAvYtelse
    object SisteVedtakErIkkeStans : KunneIkkeSimulereGjenopptakAvYtelse
}
