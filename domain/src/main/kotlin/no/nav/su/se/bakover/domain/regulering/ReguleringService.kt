package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import java.util.UUID

sealed class KunneIkkeFerdigstilleOgIverksette {
    data object KunneIkkeBeregne : KunneIkkeFerdigstilleOgIverksette()
    data object KunneIkkeSimulere : KunneIkkeFerdigstilleOgIverksette()
    data object KunneIkkeUtbetale : KunneIkkeFerdigstilleOgIverksette()
    data object KanIkkeAutomatiskRegulereSomFørerTilFeilutbetaling : KunneIkkeFerdigstilleOgIverksette()
}

sealed class KunneIkkeRegulereManuelt {
    data object FantIkkeRegulering : KunneIkkeRegulereManuelt()
    data object SimuleringFeilet : KunneIkkeRegulereManuelt()
    data object BeregningFeilet : KunneIkkeRegulereManuelt()
    data object AlleredeFerdigstilt : KunneIkkeRegulereManuelt()
    data object FantIkkeSak : KunneIkkeRegulereManuelt()
    data object StansetYtelseMåStartesFørDenKanReguleres : KunneIkkeRegulereManuelt()
    data object AvventerKravgrunnlag : KunneIkkeRegulereManuelt()
    data object HarPågåendeEllerBehovForAvkorting : KunneIkkeRegulereManuelt()
    data class KunneIkkeFerdigstille(val feil: KunneIkkeFerdigstilleOgIverksette) : KunneIkkeRegulereManuelt()
}

sealed class BeregnOgSimulerFeilet {
    data object KunneIkkeSimulere : BeregnOgSimulerFeilet()
}

sealed interface KunneIkkeOppretteRegulering {
    data object FantIkkeSak : KunneIkkeOppretteRegulering
    data object FørerIkkeTilEnEndring : KunneIkkeOppretteRegulering
    data class KunneIkkeHenteEllerOppretteRegulering(
        val feil: Sak.KunneIkkeOppretteEllerOppdatereRegulering,
    ) : KunneIkkeOppretteRegulering

    data class KunneIkkeRegulereAutomatisk(
        val feil: KunneIkkeFerdigstilleOgIverksette,
    ) : KunneIkkeOppretteRegulering

    data object UkjentFeil : KunneIkkeOppretteRegulering
}

sealed class KunneIkkeAvslutte {
    data object FantIkkeRegulering : KunneIkkeAvslutte()
    data object UgyldigTilstand : KunneIkkeAvslutte()
}

interface ReguleringService {
    fun startAutomatiskRegulering(fraOgMedMåned: Måned): List<Either<KunneIkkeOppretteRegulering, Regulering>>

    fun startAutomatiskReguleringForInnsyn(
        command: StartAutomatiskReguleringForInnsynCommand,
    )

    fun avslutt(reguleringId: UUID, avsluttetAv: NavIdentBruker): Either<KunneIkkeAvslutte, AvsluttetRegulering>
    fun hentStatus(): List<ReguleringSomKreverManuellBehandling>
    fun hentSakerMedÅpenBehandlingEllerStans(): List<Saksnummer>
    fun regulerManuelt(
        reguleringId: UUID,
        uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
        fradrag: List<Grunnlag.Fradragsgrunnlag>,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeRegulereManuelt, IverksattRegulering>
}
