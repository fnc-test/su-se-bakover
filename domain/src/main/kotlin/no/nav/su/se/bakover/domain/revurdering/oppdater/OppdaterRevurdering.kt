package no.nav.su.se.bakover.domain.revurdering.oppdater

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import java.time.Clock

fun Sak.oppdaterRevurdering(
    command: OppdaterRevurderingCommand,
    clock: Clock,
): Either<KunneIkkeOppdatereRevurdering, OpprettetRevurdering> {
    val revurderingsårsak = command.revurderingsårsak.getOrHandle {
        return when (it) {
            Revurderingsårsak.UgyldigRevurderingsårsak.UgyldigBegrunnelse -> {
                KunneIkkeOppdatereRevurdering.UgyldigBegrunnelse
            }

            Revurderingsårsak.UgyldigRevurderingsårsak.UgyldigÅrsak -> {
                KunneIkkeOppdatereRevurdering.UgyldigÅrsak
            }
        }.left()
    }
    val informasjonSomRevurderes = InformasjonSomRevurderes.tryCreate(
        revurderingsteg = command.informasjonSomRevurderes,
    ).getOrHandle {
        return KunneIkkeOppdatereRevurdering.MåVelgeInformasjonSomSkalRevurderes.left()
    }
    val revurdering = hentRevurdering(command.revurderingId).fold(
        { return KunneIkkeOppdatereRevurdering.FantIkkeRevurdering.left() },
        {
            if (it is Revurdering) it else return KunneIkkeOppdatereRevurdering.FantIkkeRevurdering.left()
        },
    )
    val periode = command.periode

    val gjeldendeVedtaksdata = hentGjeldendeVedtaksdataOgSjekkGyldighetForRevurderingsperiode(
        periode = periode,
        clock = clock,
    ).getOrHandle {
        return KunneIkkeOppdatereRevurdering.GjeldendeVedtaksdataKanIkkeRevurderes(it).left()
    }

    informasjonSomRevurderes.sjekkAtOpphørteVilkårRevurderes(gjeldendeVedtaksdata)
        .getOrHandle { return KunneIkkeOppdatereRevurdering.OpphørteVilkårMåRevurderes(it).left() }

    val avkorting = hentUteståendeAvkortingForRevurdering().fold(
        {
            it
        },
        { uteståendeAvkorting ->
            kontrollerAtUteståendeAvkortingRevurderes(
                periode = periode,
                uteståendeAvkorting = uteståendeAvkorting,
            ).getOrHandle {
                return KunneIkkeOppdatereRevurdering.UteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode(
                    avkortingsvarselperiode = uteståendeAvkorting.avkortingsvarsel.periode(),
                ).left()
            }
        },
    )

    return revurdering.oppdater(
        periode = periode,
        revurderingsårsak = revurderingsårsak,
        grunnlagsdata = gjeldendeVedtaksdata.grunnlagsdata,
        vilkårsvurderinger = gjeldendeVedtaksdata.vilkårsvurderinger,
        informasjonSomRevurderes = informasjonSomRevurderes,
        tilRevurdering = gjeldendeVedtaksdata.gjeldendeVedtakPåDato(dato = periode.fraOgMed)!!.id,
        avkorting = avkorting,
        saksbehandler = command.saksbehandler,
    )
}
