package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.manglendedokumentasjon

import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.søknadsbehandling.tilAttestering.KunneIkkeSendeSøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.vilkår.KunneIkkeLageOpplysningspliktVilkår

sealed interface KunneIkkeAvslåSøknad {
    data class KunneIkkeOppretteSøknadsbehandling(
        val underliggendeFeil: Sak.KunneIkkeOppretteSøknadsbehandling,
    ) : KunneIkkeAvslåSøknad

    data class KunneIkkeIverksetteSøknadsbehandling(
        val underliggendeFeil: no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.KunneIkkeIverksetteSøknadsbehandling,
    ) : KunneIkkeAvslåSøknad

    data class Attesteringsfeil(val feil: KunneIkkeSendeSøknadsbehandlingTilAttestering) : KunneIkkeAvslåSøknad

    data class Periodefeil(val underliggende: KunneIkkeLageOpplysningspliktVilkår) : KunneIkkeAvslåSøknad
}
