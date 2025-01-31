package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.BehandlingMedAttestering
import no.nav.su.se.bakover.domain.behandling.BehandlingMedOppgave
import no.nav.su.se.bakover.domain.behandling.MedSaksbehandlerHistorikk
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlagSkatt
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.søknad.LukkSøknadCommand
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import økonomi.domain.simulering.Simulering

sealed interface Søknadsbehandling :
    BehandlingMedOppgave,
    BehandlingMedAttestering,
    MedSaksbehandlerHistorikk<Søknadsbehandlingshendelse> {
    val søknad: Søknad.Journalført.MedOppgave

    val aldersvurdering: Aldersvurdering?
    val stønadsperiode: Stønadsperiode?
    override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling
    override val vilkårsvurderinger: Vilkårsvurderinger.Søknadsbehandling get() = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger
    override val attesteringer: Attesteringshistorikk
    val avkorting: AvkortingVedSøknadsbehandling

    override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk

    // TODO ia: fritekst bør flyttes ut av denne klassen og til et eget konsept (som også omfatter fritekst på revurderinger)
    val fritekstTilBrev: String

    val erIverksatt: Boolean get() = this is IverksattSøknadsbehandling.Avslag || this is IverksattSøknadsbehandling.Innvilget
    val erLukket: Boolean get() = this is LukketSøknadsbehandling

    val saksbehandler: NavIdentBruker.Saksbehandler
    override val beregning: Beregning?
    override val simulering: Simulering?

    fun erÅpen(): Boolean {
        return !(erIverksatt || erLukket)
    }

    /**
     * *protected* skal kun kalles fra typer som arver [Søknadsbehandling]
     * TODO jah: Flytt ut av interfacet.
     */
    fun kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike() {
        if (grunnlagsdataOgVilkårsvurderinger.periode() == null) return
        if (grunnlagsdataOgVilkårsvurderinger.periode() != periode) {
            // Det er Søknadbehandling sin oppgave og vurdere om grunnlagsdataOgVilkårsvurderinger
            // sin periode tilsvarer søknadbehandlingens periode.
            throw IllegalArgumentException("Perioden til søknadsbehandlingen: $periode var ulik grunnlagene/vilkårsvurderingene sin periode: ${grunnlagsdataOgVilkårsvurderinger.periode()}")
        }
    }

    fun leggTilSkatt(skatt: EksterneGrunnlagSkatt): Either<KunneIkkeLeggeTilSkattegrunnlag, Søknadsbehandling>

    fun lukkSøknadsbehandlingOgSøknad(
        lukkSøknadCommand: LukkSøknadCommand,
    ): Either<KunneIkkeLukkeSøknadsbehandling, LukketSøknadsbehandling> = LukketSøknadsbehandling.tryCreate(
        søknadsbehandlingSomSkalLukkes = this,
        lukkSøknadCommand = lukkSøknadCommand,
    )
}

// Her trikses det litt for å få til at funksjonen returnerer den samme konkrete typen som den kalles på.
// Teoretisk sett skal ikke UNCHECKED_CAST være noe problem i dette tilfellet siden T er begrenset til subklasser av Søknadsbehandling.
// ... i hvert fall så lenge alle subklassene av Søknadsbehandling er data classes
@Suppress("UNCHECKED_CAST")
fun <T : Søknadsbehandling> T.medFritekstTilBrev(fritekstTilBrev: String): T = (
    // Her caster vi til Søknadsbehandling for å unngå å måtte ha en else-branch
    when (val x = this as Søknadsbehandling) {
        is BeregnetSøknadsbehandling.Avslag -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is BeregnetSøknadsbehandling.Innvilget -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is IverksattSøknadsbehandling.Avslag.MedBeregning -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is IverksattSøknadsbehandling.Avslag.UtenBeregning -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is IverksattSøknadsbehandling.Innvilget -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is SimulertSøknadsbehandling -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is SøknadsbehandlingTilAttestering.Avslag.MedBeregning -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is SøknadsbehandlingTilAttestering.Avslag.UtenBeregning -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is SøknadsbehandlingTilAttestering.Innvilget -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is UnderkjentSøknadsbehandling.Avslag.MedBeregning -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is UnderkjentSøknadsbehandling.Avslag.UtenBeregning -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is UnderkjentSøknadsbehandling.Innvilget -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is VilkårsvurdertSøknadsbehandling.Avslag -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is VilkårsvurdertSøknadsbehandling.Innvilget -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is VilkårsvurdertSøknadsbehandling.Uavklart -> x.copy(fritekstTilBrev = fritekstTilBrev)
        is LukketSøknadsbehandling -> throw IllegalArgumentException("Det støttes ikke å endre fritekstTilBrev på en lukket søknadsbehandling.")
    }
    // ... og så caster vi tilbake til T for at Kotlin skal henge med i svingene
    ) as T
