package no.nav.su.se.bakover.web.routes.søknadsbehandling

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.BeregnetSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SimulertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.UnderkjentSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.web.routes.grunnlag.EksterneGrunnlagJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.grunnlag.GrunnlagsdataOgVilkårsvurderingerJson.Companion.create
import no.nav.su.se.bakover.web.routes.sak.toJson
import no.nav.su.se.bakover.web.routes.søknad.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.AldersvurderingJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.AttesteringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SøkandsbehandlingStatusJson.Companion.status
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.OppdaterStønadsperiodeRequest.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.toJson
import java.time.format.DateTimeFormatter

internal fun Søknadsbehandling.json(satsFactory: SatsFactory): String {
    return serialize(toJson(satsFactory))
}

internal fun Søknadsbehandling.toJson(satsFactory: SatsFactory): BehandlingJson {
    return when (this) {
        is VilkårsvurdertSøknadsbehandling -> BehandlingJson(
            id = id.toString(),
            søknad = søknad.toJson(),
            beregning = null,
            status = status().toString(),
            simulering = null,
            opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
            attesteringer = attesteringer.toJson(),
            saksbehandler = saksbehandler.toString(),
            fritekstTilBrev = fritekstTilBrev,
            sakId = sakId,
            stønadsperiode = stønadsperiode?.toJson(),
            grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger, satsFactory),
            erLukket = false,
            simuleringForAvkortingsvarsel = avkorting.toJson(),
            sakstype = sakstype.toJson(),
            aldersvurdering = this.aldersvurdering?.toJson(),
            eksterneGrunnlag = this.grunnlagsdataOgVilkårsvurderinger.eksterneGrunnlag.toJson(),
        )

        is BeregnetSøknadsbehandling -> {
            BehandlingJson(
                id = id.toString(),
                søknad = søknad.toJson(),
                beregning = beregning.toJson(),
                status = status().toString(),
                simulering = null,
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                attesteringer = attesteringer.toJson(),
                saksbehandler = saksbehandler.toString(),
                fritekstTilBrev = fritekstTilBrev,
                sakId = sakId,
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger, satsFactory),
                erLukket = false,
                simuleringForAvkortingsvarsel = avkorting.toJson(),
                sakstype = sakstype.toJson(),
                aldersvurdering = this.aldersvurdering.toJson(),
                eksterneGrunnlag = this.grunnlagsdataOgVilkårsvurderinger.eksterneGrunnlag.toJson(),
            )
        }

        is SimulertSøknadsbehandling -> {
            BehandlingJson(
                id = id.toString(),
                søknad = søknad.toJson(),
                beregning = beregning.toJson(),
                status = status().toString(),
                simulering = simulering.toJson(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                attesteringer = attesteringer.toJson(),
                saksbehandler = saksbehandler.toString(),
                fritekstTilBrev = fritekstTilBrev,
                sakId = sakId,
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger, satsFactory),
                erLukket = false,
                simuleringForAvkortingsvarsel = avkorting.toJson(),
                sakstype = sakstype.toJson(),
                aldersvurdering = this.aldersvurdering.toJson(),
                eksterneGrunnlag = this.grunnlagsdataOgVilkårsvurderinger.eksterneGrunnlag.toJson(),
            )
        }

        is SøknadsbehandlingTilAttestering.Innvilget -> {
            BehandlingJson(
                id = id.toString(),
                søknad = søknad.toJson(),
                beregning = beregning.toJson(),
                status = status().toString(),
                simulering = simulering.toJson(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                attesteringer = attesteringer.toJson(),
                saksbehandler = saksbehandler.toString(),
                fritekstTilBrev = fritekstTilBrev,
                sakId = sakId,
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger, satsFactory),
                erLukket = false,
                simuleringForAvkortingsvarsel = avkorting.toJson(),
                sakstype = sakstype.toJson(),
                aldersvurdering = this.aldersvurdering.toJson(),
                eksterneGrunnlag = this.grunnlagsdataOgVilkårsvurderinger.eksterneGrunnlag.toJson(),
            )
        }

        is SøknadsbehandlingTilAttestering.Avslag.MedBeregning -> {
            BehandlingJson(
                id = id.toString(),
                søknad = søknad.toJson(),
                beregning = beregning.toJson(),
                status = status().toString(),
                simulering = null,
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                attesteringer = attesteringer.toJson(),
                saksbehandler = saksbehandler.toString(),
                fritekstTilBrev = fritekstTilBrev,
                sakId = sakId,
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger, satsFactory),
                erLukket = false,
                simuleringForAvkortingsvarsel = avkorting.toJson(),
                sakstype = sakstype.toJson(),
                aldersvurdering = this.aldersvurdering.toJson(),
                eksterneGrunnlag = this.grunnlagsdataOgVilkårsvurderinger.eksterneGrunnlag.toJson(),
            )
        }

        is SøknadsbehandlingTilAttestering.Avslag.UtenBeregning -> {
            BehandlingJson(
                id = id.toString(),
                søknad = søknad.toJson(),
                beregning = null,
                status = status().toString(),
                simulering = null,
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                attesteringer = attesteringer.toJson(),
                saksbehandler = saksbehandler.toString(),
                fritekstTilBrev = fritekstTilBrev,
                sakId = sakId,
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger, satsFactory),
                erLukket = false,
                simuleringForAvkortingsvarsel = avkorting.toJson(),
                sakstype = sakstype.toJson(),
                aldersvurdering = this.aldersvurdering.toJson(),
                eksterneGrunnlag = this.grunnlagsdataOgVilkårsvurderinger.eksterneGrunnlag.toJson(),
            )
        }

        is UnderkjentSøknadsbehandling.Innvilget -> {
            BehandlingJson(
                id = id.toString(),
                søknad = søknad.toJson(),
                beregning = beregning.toJson(),
                status = status().toString(),
                simulering = simulering.toJson(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                attesteringer = attesteringer.map {
                    when (it) {
                        is Attestering.Iverksatt -> AttesteringJson(
                            attestant = it.attestant.navIdent,
                            opprettet = it.opprettet,
                            underkjennelse = null,
                        )

                        is Attestering.Underkjent -> AttesteringJson(
                            attestant = it.attestant.navIdent,
                            opprettet = it.opprettet,
                            underkjennelse = UnderkjennelseJson(
                                grunn = it.grunn.toString(),
                                kommentar = it.kommentar,
                            ),
                        )
                    }
                },
                saksbehandler = saksbehandler.toString(),
                fritekstTilBrev = fritekstTilBrev,
                sakId = sakId,
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger, satsFactory),
                erLukket = false,
                simuleringForAvkortingsvarsel = avkorting.toJson(),
                sakstype = sakstype.toJson(),
                aldersvurdering = this.aldersvurdering.toJson(),
                eksterneGrunnlag = this.grunnlagsdataOgVilkårsvurderinger.eksterneGrunnlag.toJson(),
            )
        }

        is UnderkjentSøknadsbehandling.Avslag.UtenBeregning -> {
            BehandlingJson(
                id = id.toString(),
                søknad = søknad.toJson(),
                beregning = null,
                status = status().toString(),
                simulering = null,
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                attesteringer = attesteringer.toJson(),
                saksbehandler = saksbehandler.toString(),
                fritekstTilBrev = fritekstTilBrev,
                sakId = sakId,
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger, satsFactory),
                erLukket = false,
                simuleringForAvkortingsvarsel = avkorting.toJson(),
                sakstype = sakstype.toJson(),
                aldersvurdering = this.aldersvurdering.toJson(),
                eksterneGrunnlag = this.grunnlagsdataOgVilkårsvurderinger.eksterneGrunnlag.toJson(),
            )
        }

        is UnderkjentSøknadsbehandling.Avslag.MedBeregning -> {
            BehandlingJson(
                id = id.toString(),
                søknad = søknad.toJson(),
                beregning = beregning.toJson(),
                status = status().toString(),
                simulering = null,
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                attesteringer = attesteringer.toJson(),
                saksbehandler = saksbehandler.toString(),
                fritekstTilBrev = fritekstTilBrev,
                sakId = sakId,
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger, satsFactory),
                erLukket = false,
                simuleringForAvkortingsvarsel = avkorting.toJson(),
                sakstype = sakstype.toJson(),
                aldersvurdering = this.aldersvurdering.toJson(),
                eksterneGrunnlag = this.grunnlagsdataOgVilkårsvurderinger.eksterneGrunnlag.toJson(),
            )
        }

        is IverksattSøknadsbehandling.Avslag.MedBeregning -> {
            BehandlingJson(
                id = id.toString(),
                søknad = søknad.toJson(),
                beregning = beregning.toJson(),
                status = status().toString(),
                simulering = null,
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                attesteringer = attesteringer.toJson(),
                saksbehandler = saksbehandler.toString(),
                fritekstTilBrev = fritekstTilBrev,
                sakId = sakId,
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger, satsFactory),
                erLukket = false,
                simuleringForAvkortingsvarsel = avkorting.toJson(),
                sakstype = sakstype.toJson(),
                aldersvurdering = this.aldersvurdering.toJson(),
                eksterneGrunnlag = this.grunnlagsdataOgVilkårsvurderinger.eksterneGrunnlag.toJson(),
            )
        }

        is IverksattSøknadsbehandling.Avslag.UtenBeregning -> {
            BehandlingJson(
                id = id.toString(),
                søknad = søknad.toJson(),
                beregning = null,
                status = status().toString(),
                simulering = null,
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                attesteringer = attesteringer.toJson(),
                saksbehandler = saksbehandler.toString(),
                fritekstTilBrev = fritekstTilBrev,
                sakId = sakId,
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger, satsFactory),
                erLukket = false,
                simuleringForAvkortingsvarsel = avkorting.toJson(),
                sakstype = sakstype.toJson(),
                aldersvurdering = this.aldersvurdering.toJson(),
                eksterneGrunnlag = this.grunnlagsdataOgVilkårsvurderinger.eksterneGrunnlag.toJson(),
            )
        }

        is IverksattSøknadsbehandling.Innvilget -> {
            BehandlingJson(
                id = id.toString(),
                søknad = søknad.toJson(),
                beregning = beregning.toJson(),
                status = status().toString(),
                simulering = simulering.toJson(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                attesteringer = attesteringer.toJson(),
                saksbehandler = saksbehandler.toString(),
                fritekstTilBrev = fritekstTilBrev,
                sakId = sakId,
                stønadsperiode = stønadsperiode.toJson(),
                grunnlagsdataOgVilkårsvurderinger = create(grunnlagsdata, vilkårsvurderinger, satsFactory),
                erLukket = false,
                simuleringForAvkortingsvarsel = avkorting.toJson(),
                sakstype = sakstype.toJson(),
                aldersvurdering = this.aldersvurdering.toJson(),
                eksterneGrunnlag = this.grunnlagsdataOgVilkårsvurderinger.eksterneGrunnlag.toJson(),
            )
        }

        is LukketSøknadsbehandling -> {
            underliggendeSøknadsbehandling.toJson(satsFactory).copy(erLukket = true)
        }
    }
}

internal fun HttpStatusCode.jsonBody(søknadsbehandling: Søknadsbehandling, satsFactory: SatsFactory): Resultat {
    return Resultat.json(this, serialize(søknadsbehandling.toJson(satsFactory)))
}

internal enum class SøkandsbehandlingStatusJson {
    OPPRETTET,
    VILKÅRSVURDERT_INNVILGET,
    VILKÅRSVURDERT_AVSLAG,
    BEREGNET_INNVILGET,
    BEREGNET_AVSLAG,
    SIMULERT,
    TIL_ATTESTERING_INNVILGET,
    TIL_ATTESTERING_AVSLAG,
    UNDERKJENT_INNVILGET,
    UNDERKJENT_AVSLAG,
    IVERKSATT_INNVILGET,
    IVERKSATT_AVSLAG,
    ;

    companion object {
        fun Søknadsbehandling.status(): SøkandsbehandlingStatusJson {
            return when (this) {
                is BeregnetSøknadsbehandling.Avslag -> BEREGNET_AVSLAG
                is BeregnetSøknadsbehandling.Innvilget -> BEREGNET_INNVILGET
                is IverksattSøknadsbehandling.Avslag.MedBeregning -> IVERKSATT_AVSLAG
                is IverksattSøknadsbehandling.Avslag.UtenBeregning -> IVERKSATT_AVSLAG
                is IverksattSøknadsbehandling.Innvilget -> IVERKSATT_INNVILGET
                is LukketSøknadsbehandling -> underliggendeSøknadsbehandling.status()
                is SimulertSøknadsbehandling -> SIMULERT
                is SøknadsbehandlingTilAttestering.Avslag.MedBeregning -> TIL_ATTESTERING_AVSLAG
                is SøknadsbehandlingTilAttestering.Avslag.UtenBeregning -> TIL_ATTESTERING_AVSLAG
                is SøknadsbehandlingTilAttestering.Innvilget -> TIL_ATTESTERING_INNVILGET
                is UnderkjentSøknadsbehandling.Avslag.MedBeregning -> UNDERKJENT_AVSLAG
                is UnderkjentSøknadsbehandling.Avslag.UtenBeregning -> UNDERKJENT_AVSLAG
                is UnderkjentSøknadsbehandling.Innvilget -> UNDERKJENT_INNVILGET
                is VilkårsvurdertSøknadsbehandling.Avslag -> VILKÅRSVURDERT_AVSLAG
                is VilkårsvurdertSøknadsbehandling.Innvilget -> VILKÅRSVURDERT_INNVILGET
                is VilkårsvurdertSøknadsbehandling.Uavklart -> OPPRETTET
            }
        }
    }
}
