package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.Attestering
import no.nav.su.se.bakover.common.domain.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.AvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.VurderAvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn.Companion.toAvslagsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlagSkatt
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.avslag.ErAvslag
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Aldersvurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.underkjenn.KunneIkkeUnderkjenneSøknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import java.util.UUID

sealed interface SøknadsbehandlingTilAttestering : Søknadsbehandling, KanGenerereBrev {
    abstract override val saksbehandler: NavIdentBruker.Saksbehandler
    fun nyOppgaveId(nyOppgaveId: OppgaveId): SøknadsbehandlingTilAttestering
    fun tilUnderkjent(
        attestering: Attestering.Underkjent,
    ): Either<KunneIkkeUnderkjenneSøknadsbehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson, UnderkjentSøknadsbehandling>

    abstract override val aldersvurdering: Aldersvurdering
    abstract override val attesteringer: Attesteringshistorikk
    abstract override val avkorting: AvkortingVedSøknadsbehandling.Vurdert

    override fun leggTilSkatt(skatt: EksterneGrunnlagSkatt) = KunneIkkeLeggeTilSkattegrunnlag.UgyldigTilstand.left()

    data class Innvilget(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val søknad: Søknad.Journalført.MedOppgave,
        override val oppgaveId: OppgaveId,
        override val fnr: Fnr,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val fritekstTilBrev: String,
        override val aldersvurdering: Aldersvurdering,
        override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
        override val attesteringer: Attesteringshistorikk,
        override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
        override val sakstype: Sakstype,
        override val avkorting: AvkortingVedSøknadsbehandling.KlarTilIverksetting,
    ) : SøknadsbehandlingTilAttestering, KanGenerereInnvilgelsesbrev {

        override val stønadsperiode: Stønadsperiode = aldersvurdering.stønadsperiode

        override fun skalSendeVedtaksbrev(): Boolean {
            return true
        }

        override val periode: Periode = aldersvurdering.stønadsperiode.periode

        init {
            kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
            grunnlagsdata.kastHvisIkkeAlleBosituasjonerErFullstendig()
        }

        override fun nyOppgaveId(nyOppgaveId: OppgaveId): Innvilget {
            return this.copy(oppgaveId = nyOppgaveId)
        }

        override fun tilUnderkjent(
            attestering: Attestering.Underkjent,
        ): Either<KunneIkkeUnderkjenneSøknadsbehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson, UnderkjentSøknadsbehandling.Innvilget> {
            if (attestering.attestant.navIdent == saksbehandler.navIdent) return KunneIkkeUnderkjenneSøknadsbehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            return UnderkjentSøknadsbehandling.Innvilget(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                fnr = fnr,
                beregning = beregning,
                simulering = simulering,
                saksbehandler = saksbehandler,
                attesteringer = attesteringer.leggTilNyAttestering(attestering),
                søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk,
                fritekstTilBrev = fritekstTilBrev,
                aldersvurdering = aldersvurdering,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                avkorting = avkorting,
                sakstype = sakstype,
            ).right()
        }

        fun tilIverksatt(attestering: Attestering): IverksattSøknadsbehandling.Innvilget {
            return IverksattSøknadsbehandling.Innvilget(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = oppgaveId,
                fnr = fnr,
                beregning = beregning,
                simulering = simulering,
                saksbehandler = saksbehandler,
                attesteringer = attesteringer.leggTilNyAttestering(attestering),
                søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk,
                fritekstTilBrev = fritekstTilBrev,
                aldersvurdering = aldersvurdering,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,

                avkorting = when (avkorting) {
                    is AvkortingVedSøknadsbehandling.IngenAvkorting -> avkorting
                    is AvkortingVedSøknadsbehandling.SkalAvkortes -> avkorting.avkort(id)
                },
                sakstype = sakstype,
            )
        }
    }

    sealed interface Avslag : SøknadsbehandlingTilAttestering, ErAvslag, KanGenerereAvslagsbrev {
        override val beregning: Beregning?
        abstract override val aldersvurdering: Aldersvurdering

        /** Ingenting og avkorte ved avslag. */
        override val avkorting: AvkortingVedSøknadsbehandling.IngenAvkorting get() = AvkortingVedSøknadsbehandling.IngenAvkorting

        fun iverksett(attestering: Attestering.Iverksatt): IverksattSøknadsbehandling.Avslag {
            return when (this) {
                is MedBeregning -> this.tilIverksatt(attestering)
                is UtenBeregning -> this.tilIverksatt(attestering)
            }
        }

        data class UtenBeregning(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val søknad: Søknad.Journalført.MedOppgave,
            override val oppgaveId: OppgaveId,
            override val fnr: Fnr,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val fritekstTilBrev: String,
            override val aldersvurdering: Aldersvurdering,
            override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
            override val attesteringer: Attesteringshistorikk,
            override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
            override val sakstype: Sakstype,
        ) : Avslag {
            override val beregning: Beregning? = null
            override val stønadsperiode: Stønadsperiode = aldersvurdering.stønadsperiode

            // TODO fiks typing/gyldig tilstand/vilkår fradrag?
            override val avslagsgrunner: List<Avslagsgrunn> = when (val vilkår = vilkårsvurderinger.vurdering) {
                is Vilkårsvurderingsresultat.Avslag -> vilkår.avslagsgrunner
                is Vilkårsvurderingsresultat.Innvilget -> emptyList()
                is Vilkårsvurderingsresultat.Uavklart -> emptyList()
            }

            override val periode: Periode = aldersvurdering.stønadsperiode.periode
            override val simulering: Simulering? = null

            init {
                kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
            }

            override fun skalSendeVedtaksbrev(): Boolean {
                return true
            }

            override fun nyOppgaveId(nyOppgaveId: OppgaveId): UtenBeregning {
                return this.copy(oppgaveId = nyOppgaveId)
            }

            override fun tilUnderkjent(
                attestering: Attestering.Underkjent,
            ): Either<KunneIkkeUnderkjenneSøknadsbehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson, UnderkjentSøknadsbehandling.Avslag.UtenBeregning> {
                if (attestering.attestant.navIdent == saksbehandler.navIdent) {
                    return KunneIkkeUnderkjenneSøknadsbehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
                }
                return UnderkjentSøknadsbehandling.Avslag.UtenBeregning(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    fnr = fnr,
                    saksbehandler = saksbehandler,
                    attesteringer = attesteringer.leggTilNyAttestering(attestering),
                    søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk,
                    fritekstTilBrev = fritekstTilBrev,
                    aldersvurdering = aldersvurdering,
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,

                    sakstype = sakstype,
                ).right()
            }

            fun tilIverksatt(
                attestering: Attestering,
            ): IverksattSøknadsbehandling.Avslag.UtenBeregning {
                return IverksattSøknadsbehandling.Avslag.UtenBeregning(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    fnr = fnr,
                    saksbehandler = saksbehandler,
                    attesteringer = attesteringer.leggTilNyAttestering(attestering),
                    søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk,
                    fritekstTilBrev = fritekstTilBrev,
                    aldersvurdering = aldersvurdering,
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,

                    sakstype = sakstype,
                )
            }
        }

        data class MedBeregning(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val søknad: Søknad.Journalført.MedOppgave,
            override val oppgaveId: OppgaveId,
            override val fnr: Fnr,
            override val beregning: Beregning,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val fritekstTilBrev: String,
            override val aldersvurdering: Aldersvurdering,
            override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling,
            override val attesteringer: Attesteringshistorikk,
            override val søknadsbehandlingsHistorikk: Søknadsbehandlingshistorikk,
            override val sakstype: Sakstype,
        ) : Avslag {
            override val stønadsperiode: Stønadsperiode = aldersvurdering.stønadsperiode
            private val avslagsgrunnForBeregning: List<Avslagsgrunn> =
                when (val vurdering = VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
                    is AvslagGrunnetBeregning.Ja -> listOf(vurdering.grunn.toAvslagsgrunn())
                    is AvslagGrunnetBeregning.Nei -> emptyList()
                }

            // TODO fiks typing/gyldig tilstand/vilkår fradrag?
            override val avslagsgrunner: List<Avslagsgrunn> = when (val vilkår = vilkårsvurderinger.vurdering) {
                is Vilkårsvurderingsresultat.Avslag -> vilkår.avslagsgrunner
                is Vilkårsvurderingsresultat.Innvilget -> emptyList()
                is Vilkårsvurderingsresultat.Uavklart -> emptyList()
            } + avslagsgrunnForBeregning

            override val periode: Periode = aldersvurdering.stønadsperiode.periode
            override val simulering: Simulering? = null

            init {
                kastHvisGrunnlagsdataOgVilkårsvurderingerPeriodenOgBehandlingensPerioderErUlike()
                grunnlagsdata.kastHvisIkkeAlleBosituasjonerErFullstendig()
            }

            override fun skalSendeVedtaksbrev(): Boolean {
                return true
            }

            override fun nyOppgaveId(nyOppgaveId: OppgaveId): MedBeregning {
                return this.copy(oppgaveId = nyOppgaveId)
            }

            override fun tilUnderkjent(
                attestering: Attestering.Underkjent,
            ): Either<KunneIkkeUnderkjenneSøknadsbehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson, UnderkjentSøknadsbehandling.Avslag.MedBeregning> {
                if (attestering.attestant.navIdent == saksbehandler.navIdent) return KunneIkkeUnderkjenneSøknadsbehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()

                return UnderkjentSøknadsbehandling.Avslag.MedBeregning(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    fnr = fnr,
                    beregning = beregning,
                    saksbehandler = saksbehandler,
                    attesteringer = attesteringer.leggTilNyAttestering(attestering),
                    søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk,
                    fritekstTilBrev = fritekstTilBrev,
                    aldersvurdering = aldersvurdering,
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                    sakstype = sakstype,
                ).right()
            }

            internal fun tilIverksatt(
                attestering: Attestering,
            ): IverksattSøknadsbehandling.Avslag.MedBeregning {
                return IverksattSøknadsbehandling.Avslag.MedBeregning(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    søknad = søknad,
                    oppgaveId = oppgaveId,
                    fnr = fnr,
                    beregning = beregning,
                    saksbehandler = saksbehandler,
                    attesteringer = attesteringer.leggTilNyAttestering(attestering),
                    søknadsbehandlingsHistorikk = søknadsbehandlingsHistorikk,
                    fritekstTilBrev = fritekstTilBrev,
                    aldersvurdering = aldersvurdering,
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,

                    sakstype = sakstype,
                )
            }
        }
    }
}
