package no.nav.su.se.bakover.service.revurdering

import no.nav.su.se.bakover.domain.vilkår.KunneIkkeLageOpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import java.util.UUID

sealed class LeggTilOpplysningspliktRequest {
    abstract val behandlingId: UUID
    abstract val vilkår: OpplysningspliktVilkår.Vurdert

    data class Søknadsbehandling(
        override val behandlingId: UUID,
        override val vilkår: OpplysningspliktVilkår.Vurdert,
    ) : LeggTilOpplysningspliktRequest()

    data class Revurdering(
        override val behandlingId: UUID,
        override val vilkår: OpplysningspliktVilkår.Vurdert,
    ) : LeggTilOpplysningspliktRequest()
}

sealed interface KunneIkkeLeggeTilOpplysningsplikt {
    data class Søknadsbehandling(val feil: no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling.KunneIkkeLeggeTilOpplysningsplikt) :
        KunneIkkeLeggeTilOpplysningsplikt

    data class Revurdering(val feil: no.nav.su.se.bakover.domain.revurdering.Revurdering.KunneIkkeLeggeTilOpplysningsplikt) :
        KunneIkkeLeggeTilOpplysningsplikt

    object FantIkkeBehandling : KunneIkkeLeggeTilOpplysningsplikt

    data class UgyldigOpplysningspliktVilkår(val feil: KunneIkkeLageOpplysningspliktVilkår) :
        KunneIkkeLeggeTilOpplysningsplikt
}
