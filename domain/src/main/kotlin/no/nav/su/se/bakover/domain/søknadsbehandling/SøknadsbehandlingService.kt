package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument
import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageGrunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.fradrag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.domain.revurdering.vilkår.bosituasjon.KunneIkkeLeggeTilBosituasjongrunnlag
import no.nav.su.se.bakover.domain.revurdering.vilkår.bosituasjon.LeggTilBosituasjonerRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.SaksbehandlersAvgjørelse
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.UgyldigFamiliegjenforeningVilkår
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening.LeggTilFamiliegjenforeningRequest
import no.nav.su.se.bakover.domain.vilkår.fastopphold.KunneIkkeLeggeFastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.fastopphold.LeggTilFastOppholdINorgeRequest
import no.nav.su.se.bakover.domain.vilkår.flyktning.KunneIkkeLeggeTilFlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.flyktning.LeggTilFlyktningVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.formue.LeggTilFormuevilkårRequest
import no.nav.su.se.bakover.domain.vilkår.institusjonsopphold.KunneIkkeLeggeTilInstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.institusjonsopphold.LeggTilInstitusjonsoppholdVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.lovligopphold.KunneIkkeLeggetilLovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.lovligopphold.LeggTilLovligOppholdRequest
import no.nav.su.se.bakover.domain.vilkår.opplysningsplikt.KunneIkkeLeggeTilOpplysningsplikt
import no.nav.su.se.bakover.domain.vilkår.opplysningsplikt.LeggTilOpplysningspliktRequest
import no.nav.su.se.bakover.domain.vilkår.oppmøte.KunneIkkeLeggeTilPersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.oppmøte.LeggTilPersonligOppmøteVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.pensjon.KunneIkkeLeggeTilPensjonsVilkår
import no.nav.su.se.bakover.domain.vilkår.pensjon.LeggTilPensjonsVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.uføre.LeggTilUførevurderingerRequest
import no.nav.su.se.bakover.domain.vilkår.utenlandsopphold.LeggTilFlereUtenlandsoppholdRequest
import java.util.UUID
import kotlin.reflect.KClass

interface SøknadsbehandlingService {
    fun opprett(
        request: OpprettRequest,
        hentSak: (() -> Sak)? = null,
    ): Either<Sak.KunneIkkeOppretteSøknadsbehandling, Pair<Sak, VilkårsvurdertSøknadsbehandling.Uavklart>>

    fun beregn(request: BeregnRequest): Either<KunneIkkeBeregne, BeregnetSøknadsbehandling>
    fun simuler(request: SimulerRequest): Either<KunneIkkeSimulereBehandling, SimulertSøknadsbehandling>
    fun sendTilAttestering(request: SendTilAttesteringRequest): Either<KunneIkkeSendeTilAttestering, SøknadsbehandlingTilAttestering>
    fun underkjenn(request: UnderkjennRequest): Either<KunneIkkeUnderkjenne, UnderkjentSøknadsbehandling>

    fun brev(request: BrevRequest): Either<KunneIkkeLageDokument, ByteArray>
    fun hent(request: HentRequest): Either<FantIkkeBehandling, Søknadsbehandling>

    /**
     * Oppdatering av stønadsperiode tar hensyn til personens alder ved slutten av stønadsperioden.
     */
    fun oppdaterStønadsperiode(request: OppdaterStønadsperiodeRequest): Either<Sak.KunneIkkeOppdatereStønadsperiode, VilkårsvurdertSøknadsbehandling>
    fun leggTilUførevilkår(
        request: LeggTilUførevurderingerRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilUføreVilkår, Søknadsbehandling>

    fun leggTilLovligOpphold(
        request: LeggTilLovligOppholdRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggetilLovligOppholdVilkår, Søknadsbehandling>

    fun leggTilFamiliegjenforeningvilkår(
        request: LeggTilFamiliegjenforeningRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilFamiliegjenforeningVilkårService, Søknadsbehandling>

    fun leggTilFradragsgrunnlag(
        request: LeggTilFradragsgrunnlagRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilFradragsgrunnlag, Søknadsbehandling>

    fun leggTilFormuevilkår(
        request: LeggTilFormuevilkårRequest,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFormuevilkår, Søknadsbehandling>

    fun hentForSøknad(søknadId: UUID): Søknadsbehandling?
    fun persisterSøknadsbehandling(lukketSøknadbehandling: LukketSøknadsbehandling, tx: TransactionContext)
    fun leggTilUtenlandsopphold(
        request: LeggTilFlereUtenlandsoppholdRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilUtenlandsopphold, VilkårsvurdertSøknadsbehandling>

    fun leggTilOpplysningspliktVilkår(request: LeggTilOpplysningspliktRequest.Søknadsbehandling): Either<KunneIkkeLeggeTilOpplysningsplikt, VilkårsvurdertSøknadsbehandling>

    fun leggTilPensjonsVilkår(
        request: LeggTilPensjonsVilkårRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilPensjonsVilkår, VilkårsvurdertSøknadsbehandling>

    fun leggTilFlyktningVilkår(
        request: LeggTilFlyktningVilkårRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilFlyktningVilkår, VilkårsvurdertSøknadsbehandling>

    fun leggTilFastOppholdINorgeVilkår(
        request: LeggTilFastOppholdINorgeRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeFastOppholdINorgeVilkår, VilkårsvurdertSøknadsbehandling>

    fun leggTilPersonligOppmøteVilkår(
        request: LeggTilPersonligOppmøteVilkårRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilPersonligOppmøteVilkår, VilkårsvurdertSøknadsbehandling>

    fun leggTilInstitusjonsoppholdVilkår(
        request: LeggTilInstitusjonsoppholdVilkårRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilInstitusjonsoppholdVilkår, VilkårsvurdertSøknadsbehandling>

    fun leggTilBosituasjongrunnlag(
        request: LeggTilBosituasjonerRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilBosituasjongrunnlag, VilkårsvurdertSøknadsbehandling>

    fun leggTilEksternSkattegrunnlag(
        behandlingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilSkattegrunnlag, Søknadsbehandling>

    data class OpprettRequest(
        val søknadId: UUID,
        val sakId: UUID,
        val saksbehandler: NavIdentBruker.Saksbehandler,
    )

    sealed interface KunneIkkeVilkårsvurdere {
        data object FantIkkeBehandling : KunneIkkeVilkårsvurdere
    }

    data class BeregnRequest(
        val behandlingId: UUID,
        val begrunnelse: String?,
        val saksbehandler: NavIdentBruker.Saksbehandler,
    )

    sealed interface KunneIkkeBeregne {
        data object FantIkkeBehandling : KunneIkkeBeregne
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
        ) : KunneIkkeBeregne {
            val til: KClass<out BeregnetSøknadsbehandling> = BeregnetSøknadsbehandling::class
        }

        data class UgyldigTilstandForEndringAvFradrag(val feil: KunneIkkeLeggeTilFradragsgrunnlag) : KunneIkkeBeregne
        data object AvkortingErUfullstendig : KunneIkkeBeregne
    }

    data class SimulerRequest(
        val behandlingId: UUID,
        val saksbehandler: NavIdentBruker.Saksbehandler,
    )

    sealed interface KunneIkkeSimulereBehandling {
        data class KunneIkkeSimulere(val feil: no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeSimulereBehandling) :
            KunneIkkeSimulereBehandling

        data object FantIkkeBehandling : KunneIkkeSimulereBehandling
    }

    data class SendTilAttesteringRequest(
        val behandlingId: UUID,
        val saksbehandler: NavIdentBruker.Saksbehandler,
        val fritekstTilBrev: String,
    )

    sealed interface KunneIkkeSendeTilAttestering {
        data object KunneIkkeFinneAktørId : KunneIkkeSendeTilAttestering
        data object KunneIkkeOppretteOppgave : KunneIkkeSendeTilAttestering
        data class HarValideringsfeil(val feil: ValideringsfeilAttestering) : KunneIkkeSendeTilAttestering
    }

    data class UnderkjennRequest(
        val behandlingId: UUID,
        val attestering: Attestering.Underkjent,
    )

    sealed interface KunneIkkeUnderkjenne {
        data object FantIkkeBehandling : KunneIkkeUnderkjenne
        data object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeUnderkjenne
        data object KunneIkkeOppretteOppgave : KunneIkkeUnderkjenne
        data object FantIkkeAktørId : KunneIkkeUnderkjenne
    }

    sealed interface BrevRequest {
        abstract val behandling: Søknadsbehandling

        data class MedFritekst(
            override val behandling: Søknadsbehandling,
            val fritekst: String,
        ) : BrevRequest

        data class UtenFritekst(
            override val behandling: Søknadsbehandling,
        ) : BrevRequest
    }

    sealed interface KunneIkkeLageBrev {
        data object KunneIkkeLagePDF : KunneIkkeLageBrev
        data object FantIkkePerson : KunneIkkeLageBrev
        data object FikkIkkeHentetSaksbehandlerEllerAttestant : KunneIkkeLageBrev
        data object KunneIkkeFinneGjeldendeUtbetaling : KunneIkkeLageBrev
    }

    data class HentRequest(
        val behandlingId: UUID,
    )

    data object FantIkkeBehandling

    data class OppdaterStønadsperiodeRequest(
        val behandlingId: UUID,
        val stønadsperiode: Stønadsperiode,
        val sakId: UUID,
        val saksbehandler: NavIdentBruker.Saksbehandler,
        val saksbehandlersAvgjørelse: SaksbehandlersAvgjørelse?,
    )

    sealed interface KunneIkkeLeggeTilUføreVilkår {
        data object FantIkkeBehandling : KunneIkkeLeggeTilUføreVilkår
        data object VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden : KunneIkkeLeggeTilUføreVilkår
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling>,
        ) : KunneIkkeLeggeTilUføreVilkår

        data class UgyldigInput(
            val originalFeil: LeggTilUførevurderingerRequest.UgyldigUførevurdering,
        ) : KunneIkkeLeggeTilUføreVilkår
    }

    sealed interface KunneIkkeLeggeTilFamiliegjenforeningVilkårService {
        data object FantIkkeBehandling : KunneIkkeLeggeTilFamiliegjenforeningVilkårService
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling>,
        ) : KunneIkkeLeggeTilFamiliegjenforeningVilkårService

        data class UgyldigFamiliegjenforeningVilkårService(val feil: UgyldigFamiliegjenforeningVilkår) :
            KunneIkkeLeggeTilFamiliegjenforeningVilkårService

        fun KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFamiliegjenforeningVilkår.tilKunneIkkeLeggeTilFamiliegjenforeningVilkårService() =
            when (this) {
                is KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFamiliegjenforeningVilkår.UgyldigTilstand -> UgyldigTilstand(
                    fra = this.fra,
                    til = this.til,
                )
            }
    }

    sealed interface KunneIkkeFullføreBosituasjonGrunnlag {
        data object FantIkkeBehandling : KunneIkkeFullføreBosituasjonGrunnlag

        data object KlarteIkkeLagreBosituasjon : KunneIkkeFullføreBosituasjonGrunnlag
        data object KlarteIkkeHentePersonIPdl : KunneIkkeFullføreBosituasjonGrunnlag
        data class KunneIkkeEndreBosituasjongrunnlag(val feil: KunneIkkeLeggeTilGrunnlag.KunneIkkeOppdatereBosituasjon) :
            KunneIkkeFullføreBosituasjonGrunnlag
    }

    sealed interface KunneIkkeLeggeTilFradragsgrunnlag {
        data object FantIkkeBehandling : KunneIkkeLeggeTilFradragsgrunnlag
        data object GrunnlagetMåVæreInnenforBehandlingsperioden : KunneIkkeLeggeTilFradragsgrunnlag
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling>,
        ) : KunneIkkeLeggeTilFradragsgrunnlag

        data class KunneIkkeEndreFradragsgrunnlag(val feil: KunneIkkeLageGrunnlagsdata) :
            KunneIkkeLeggeTilFradragsgrunnlag
    }

    sealed interface KunneIkkeLeggeTilUtenlandsopphold {
        data object FantIkkeBehandling : KunneIkkeLeggeTilUtenlandsopphold
        data object VurderingsperiodeUtenforBehandlingsperiode : KunneIkkeLeggeTilUtenlandsopphold
        data object AlleVurderingsperioderMåHaSammeResultat : KunneIkkeLeggeTilUtenlandsopphold
        data object MåInneholdeKunEnVurderingsperiode : KunneIkkeLeggeTilUtenlandsopphold
        data object MåVurdereHelePerioden : KunneIkkeLeggeTilUtenlandsopphold
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling>,
        ) : KunneIkkeLeggeTilUtenlandsopphold
    }
}
