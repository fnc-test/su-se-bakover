package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.KunneIkkeBekrefteKlagesteg
import no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevForKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeOppretteKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeOversendeKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeSendeTilAttestering
import no.nav.su.se.bakover.domain.klage.KunneIkkeUnderkjenne
import no.nav.su.se.bakover.domain.klage.KunneIkkeVilkårsvurdereKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeVurdereKlage
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import java.util.UUID

interface KlageService {
    fun opprett(request: NyKlageRequest): Either<KunneIkkeOppretteKlage, OpprettetKlage>

    fun vilkårsvurder(request: VurderKlagevilkårRequest): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage>
    fun bekreftVilkårsvurderinger(
        klageId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeBekrefteKlagesteg, VilkårsvurdertKlage.Bekreftet>

    fun vurder(request: KlageVurderingerRequest): Either<KunneIkkeVurdereKlage, VurdertKlage>
    fun bekreftVurderinger(
        klageId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeBekrefteKlagesteg, VurdertKlage.Bekreftet>

    fun sendTilAttestering(
        klageId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeSendeTilAttestering, KlageTilAttestering>

    fun underkjenn(request: UnderkjennKlageRequest): Either<KunneIkkeUnderkjenne, VurdertKlage.Bekreftet>

    fun oversend(klageId: UUID, attestant: NavIdentBruker.Attestant): Either<KunneIkkeOversendeKlage, OversendtKlage>

    fun brevutkast(
        klageId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        fritekst: String,
    ): Either<KunneIkkeLageBrevutkast, ByteArray>
}

sealed class KunneIkkeLageBrevutkast {
    object FantIkkeKlage : KunneIkkeLageBrevutkast()
    data class GenereringAvBrevFeilet(val feil: KunneIkkeLageBrevForKlage) : KunneIkkeLageBrevutkast()
}