package no.nav.su.se.bakover.test

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.OpplysningspliktBeskrivelse
import no.nav.su.se.bakover.domain.grunnlag.Opplysningspliktgrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.grunnlag.Utenlandsoppholdgrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeOpplysningsplikt
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeUtenlandsopphold
import java.util.UUID

/**
 * 100% uføregrad
 * 0 forventet inntekt
 * */
fun uføregrunnlagForventetInntekt0(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): Grunnlag.Uføregrunnlag {
    return uføregrunnlagForventetInntekt(
        id = id,
        opprettet = opprettet,
        periode = periode,
        forventetInntekt = 0,
    )
}

/**
 * 100% uføregrad
 * 12000 forventet inntekt per år / 1000 per måned
 * */
fun uføregrunnlagForventetInntekt12000(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): Grunnlag.Uføregrunnlag {
    return uføregrunnlagForventetInntekt(
        id = id,
        opprettet = opprettet,
        periode = periode,
        forventetInntekt = 12000,
    )
}

/** 100% uføregrad */
fun uføregrunnlagForventetInntekt(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    forventetInntekt: Int,
): Grunnlag.Uføregrunnlag {
    return Grunnlag.Uføregrunnlag(
        id = id,
        opprettet = opprettet,
        periode = periode,
        uføregrad = Uføregrad.parse(100),
        forventetInntekt = forventetInntekt,
    )
}

fun uføregrunnlag(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    forventetInntekt: Int = 0,
    uføregrad: Uføregrad = Uføregrad.parse(100),
): Grunnlag.Uføregrunnlag {
    return Grunnlag.Uføregrunnlag(
        id = id,
        opprettet = opprettet,
        periode = periode,
        uføregrad = uføregrad,
        forventetInntekt = forventetInntekt,
    )
}

fun innvilgetUførevilkårForventetInntekt0(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    uføregrunnlag: Grunnlag.Uføregrunnlag = uføregrunnlagForventetInntekt0(
        id = UUID.randomUUID(),
        opprettet = opprettet,
        periode = periode,
    ),
): Vilkår.Uførhet.Vurdert {
    return Vilkår.Uførhet.Vurdert.create(
        vurderingsperioder = nonEmptyListOf(
            Vurderingsperiode.Uføre.create(
                id = id,
                opprettet = opprettet,
                resultat = Resultat.Innvilget,
                grunnlag = uføregrunnlag,
                periode = periode,
                begrunnelse = "innvilgetUførevilkårForventetInntekt0",
            ),
        ),
    )
}

fun utenlandsoppholdInnvilget(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    grunnlag: Utenlandsoppholdgrunnlag? = null,
): UtenlandsoppholdVilkår.Vurdert {
    return UtenlandsoppholdVilkår.Vurdert.tryCreate(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodeUtenlandsopphold.create(
                id = id,
                opprettet = opprettet,
                resultat = Resultat.Innvilget,
                grunnlag = grunnlag,
                periode = periode,
                begrunnelse = "begrunnelse",
            ),
        ),
    ).getOrFail()
}

fun tilstrekkeligDokumentert(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): OpplysningspliktVilkår.Vurdert {
    return OpplysningspliktVilkår.Vurdert.createFromVilkårsvurderinger(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodeOpplysningsplikt.create(
                id = id,
                opprettet = opprettet,
                periode = periode,
                grunnlag = Opplysningspliktgrunnlag(
                    id = id,
                    opprettet = opprettet,
                    periode = periode,
                    beskrivelse = OpplysningspliktBeskrivelse.TilstrekkeligDokumentasjon,
                ),
            ),
        ),
    )
}

fun utilstrekkeligDokumentert(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): OpplysningspliktVilkår.Vurdert {
    return OpplysningspliktVilkår.Vurdert.createFromVilkårsvurderinger(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodeOpplysningsplikt.create(
                id = id,
                opprettet = opprettet,
                grunnlag = Opplysningspliktgrunnlag(
                    id = id,
                    opprettet = opprettet,
                    periode = periode,
                    beskrivelse = OpplysningspliktBeskrivelse.UtilstrekkeligDokumentasjon,
                ),
                periode = periode,
            ),
        ),
    )
}

fun utenlandsoppholdAvslag(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): UtenlandsoppholdVilkår.Vurdert {
    return UtenlandsoppholdVilkår.Vurdert.tryCreate(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodeUtenlandsopphold.create(
                id = id,
                opprettet = opprettet,
                resultat = Resultat.Avslag,
                grunnlag = null,
                periode = periode,
                begrunnelse = "begrunnelse",
            ),
        ),
    ).getOrFail()
}

fun innvilgetUførevilkårForventetInntekt12000(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): Vilkår.Uførhet.Vurdert {
    return Vilkår.Uførhet.Vurdert.create(
        vurderingsperioder = nonEmptyListOf(
            Vurderingsperiode.Uføre.create(
                id = UUID.randomUUID(),
                opprettet = opprettet,
                resultat = Resultat.Innvilget,
                grunnlag = uføregrunnlagForventetInntekt12000(opprettet = opprettet, periode = periode),
                periode = periode,
                begrunnelse = "innvilgetUførevilkårForventetInntekt12000",
            ),
        ),
    )
}

fun innvilgetUførevilkår(
    vurderingsperiodeId: UUID = UUID.randomUUID(),
    grunnlagsId: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    begrunnelse: String? = "innvilgetUførevilkårForventetInntekt0",
    forventetInntekt: Int = 0,
    uføregrad: Uføregrad = Uføregrad.parse(100),
): Vilkår.Uførhet.Vurdert {
    return Vilkår.Uførhet.Vurdert.create(
        vurderingsperioder = nonEmptyListOf(
            Vurderingsperiode.Uføre.create(
                id = vurderingsperiodeId,
                opprettet = opprettet,
                resultat = Resultat.Innvilget,
                grunnlag = uføregrunnlag(
                    id = grunnlagsId,
                    opprettet = opprettet,
                    periode = periode,
                    forventetInntekt = forventetInntekt,
                    uføregrad = uføregrad,
                ),
                periode = periode,
                begrunnelse = begrunnelse,
            ),
        ),
    )
}

fun avslåttUførevilkårUtenGrunnlag(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): Vilkår.Uførhet.Vurdert {
    return Vilkår.Uførhet.Vurdert.create(
        vurderingsperioder = nonEmptyListOf(
            Vurderingsperiode.Uføre.create(
                id = UUID.randomUUID(),
                opprettet = opprettet,
                resultat = Resultat.Avslag,
                grunnlag = null,
                periode = periode,
                begrunnelse = "avslåttUførevilkårUtenGrunnlag",
            ),
        ),
    )
}

fun formueGrunnlagUtenEps0Innvilget(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig,
): Formuegrunnlag {
    return Formuegrunnlag.create(
        id = UUID.randomUUID(),
        opprettet = opprettet,
        periode = periode,
        epsFormue = null,
        søkersFormue = Formuegrunnlag.Verdier.create(
            verdiIkkePrimærbolig = 0,
            verdiEiendommer = 0,
            verdiKjøretøy = 0,
            innskudd = 0,
            verdipapir = 0,
            pengerSkyldt = 0,
            kontanter = 0,
            depositumskonto = 0,
        ),
        begrunnelse = null,
        bosituasjon = bosituasjon,
        behandlingsPeriode = periode,
    )
}

fun formueGrunnlagUtenEpsAvslått(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig,
): Formuegrunnlag {
    return Formuegrunnlag.create(
        id = UUID.randomUUID(),
        opprettet = opprettet,
        periode = periode,
        epsFormue = null,
        søkersFormue = Formuegrunnlag.Verdier.create(
            verdiIkkePrimærbolig = 0,
            verdiEiendommer = 0,
            verdiKjøretøy = 0,
            innskudd = 1000000,
            verdipapir = 0,
            pengerSkyldt = 0,
            kontanter = 0,
            depositumskonto = 0,
        ),
        begrunnelse = null,
        bosituasjon = bosituasjon,
        behandlingsPeriode = periode,
    )
}

fun formuevilkårUtenEps0Innvilget(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig,
): Vilkår.Formue {
    return Vilkår.Formue.Vurdert.createFromVilkårsvurderinger(
        vurderingsperioder = nonEmptyListOf(
            Vurderingsperiode.Formue.create(
                id = UUID.randomUUID(),
                opprettet = opprettet,
                periode = periode,
                resultat = Resultat.Innvilget,
                grunnlag = formueGrunnlagUtenEps0Innvilget(opprettet, periode, bosituasjon),
            ),
        ),
    )
}

fun formuevilkårAvslåttPgrBrukersformue(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig,
): Vilkår.Formue {
    return Vilkår.Formue.Vurdert.createFromVilkårsvurderinger(
        vurderingsperioder = nonEmptyListOf(
            Vurderingsperiode.Formue.create(
                id = UUID.randomUUID(),
                opprettet = opprettet,
                periode = periode,
                resultat = Resultat.Avslag,
                grunnlag = formueGrunnlagUtenEpsAvslått(opprettet, periode, bosituasjon),
            ),
        ),
    )
}

/**
 * periode: 2021
 * uføre: innvilget med forventet inntekt 0
 * bosituasjon: enslig
 * formue (via behandlingsinformasjon): ingen eps, sum 0
 * utenlandsopphold: innvilget
 */
fun vilkårsvurderingerSøknadsbehandlingInnvilget(
    periode: Periode = år(2021),
    uføre: Vilkår.Uførhet = innvilgetUførevilkårForventetInntekt0(
        id = UUID.randomUUID(),
        periode = periode,
    ),
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig = bosituasjongrunnlagEnslig(
        id = UUID.randomUUID(),
        periode = periode,
    ),
    behandlingsinformasjon: Behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
    utenlandsopphold: UtenlandsoppholdVilkår = utenlandsoppholdInnvilget(
        id = UUID.randomUUID(),
        periode = periode,
    ),
    opplysningsplikt: OpplysningspliktVilkår = tilstrekkeligDokumentert(
        id = UUID.randomUUID(),
        periode = periode
    ),
): Vilkårsvurderinger.Søknadsbehandling {
    return Vilkårsvurderinger.Søknadsbehandling(
        uføre = uføre,
        utenlandsopphold = utenlandsopphold,
        opplysningsplikt = opplysningsplikt,
    ).oppdater(
        stønadsperiode = Stønadsperiode.create(periode = periode, begrunnelse = ""),
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = Grunnlagsdata.tryCreate(
            fradragsgrunnlag = emptyList(),
            bosituasjon = listOf(bosituasjon),
        ).getOrFail(),
        clock = fixedClock,
    )
}

fun vilkårsvurderingerRevurderingInnvilget(
    periode: Periode = år(2021),
    uføre: Vilkår.Uførhet = innvilgetUførevilkårForventetInntekt0(periode = periode),
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig = bosituasjongrunnlagEnslig(periode = periode),
    formue: Vilkår.Formue = formuevilkårUtenEps0Innvilget(periode = periode, bosituasjon = bosituasjon),
    utenlandsopphold: UtenlandsoppholdVilkår = utenlandsoppholdInnvilget(periode = periode),
    opplysningsplikt: OpplysningspliktVilkår = tilstrekkeligDokumentert(periode = periode),
): Vilkårsvurderinger.Revurdering {
    return Vilkårsvurderinger.Revurdering(
        uføre = uføre,
        formue = formue,
        utenlandsopphold = utenlandsopphold,
        opplysningsplikt = opplysningsplikt,
    )
}

fun vilkårsvurderingerAvslåttAlleRevurdering(
    periode: Periode = år(2021),
    uføre: Vilkår.Uførhet = avslåttUførevilkårUtenGrunnlag(periode = periode),
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig = bosituasjongrunnlagEnslig(periode = periode),
    formue: Vilkår.Formue = formuevilkårAvslåttPgrBrukersformue(periode = periode, bosituasjon = bosituasjon),
    utenlandsopphold: UtenlandsoppholdVilkår = utenlandsoppholdAvslag(periode = periode),
    opplysningsplikt: OpplysningspliktVilkår = utilstrekkeligDokumentert(periode = periode),
): Vilkårsvurderinger.Revurdering {
    return Vilkårsvurderinger.Revurdering(
        uføre = uføre,
        formue = formue,
        utenlandsopphold = utenlandsopphold,
        opplysningsplikt = opplysningsplikt,
    )
}

/**
 * Defaults:
 * periode: 2021
 * bosituasjon: enslig
 *
 * Predefined:
 * uføre: avslag
 * formue: innvilget
 */
@Suppress("unused")
fun vilkårsvurderingerAvslåttAlle(
    periode: Periode = år(2021),
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig = bosituasjongrunnlagEnslig(periode = periode),
): Vilkårsvurderinger.Søknadsbehandling {
    return Vilkårsvurderinger.Søknadsbehandling(
        uføre = avslåttUførevilkårUtenGrunnlag(
            periode = periode,
        ),
        utenlandsopphold = utenlandsoppholdAvslag(periode = periode),
        opplysningsplikt = utilstrekkeligDokumentert(periode = periode)
    ).oppdater(
        stønadsperiode = Stønadsperiode.create(
            periode = periode,
            begrunnelse = "",
        ),
        behandlingsinformasjon = behandlingsinformasjonAlleVilkårAvslått,
        grunnlagsdata = Grunnlagsdata.tryCreate(
            fradragsgrunnlag = listOf(),
            bosituasjon = listOf(bosituasjon),
        ).getOrFail(),
        clock = fixedClock,
    )
}

/**
 * Defaults:
 * periode: 2021
 * bosituasjon: enslig
 *
 * Predefined:
 * uføre: avslag
 * formue: innvilget
 */
fun vilkårsvurderingerAvslåttUføreOgAndreInnvilget(
    periode: Periode = år(2021),
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig = bosituasjongrunnlagEnslig(periode = periode),
): Vilkårsvurderinger.Revurdering {
    return Vilkårsvurderinger.Revurdering(
        uføre = avslåttUførevilkårUtenGrunnlag(periode = periode),
        formue = formuevilkårUtenEps0Innvilget(periode = periode, bosituasjon = bosituasjon),
        utenlandsopphold = utenlandsoppholdInnvilget(periode = periode),
        opplysningsplikt = tilstrekkeligDokumentert(periode = periode),
    )
}

fun avslåttFormueVilkår(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode,
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig = Grunnlag.Bosituasjon.Fullstendig.Enslig(
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
        periode = periode,
        begrunnelse = null,
    ),
): Vilkår.Formue.Vurdert {
    val (søkerVerdi, epsVerdi) = when (bosituasjon.harEPS()) {
        true -> {
            Formuegrunnlag.Verdier.empty().copy(verdiEiendommer = 15_000) to
                Formuegrunnlag.Verdier.empty().copy(verdiEiendommer = 150_000)
        }
        false -> {
            Formuegrunnlag.Verdier.empty().copy(verdiEiendommer = 150_000) to
                null
        }
    }
    return Vilkår.Formue.Vurdert.createFromGrunnlag(
        nonEmptyListOf(
            Formuegrunnlag.create(
                id = id,
                opprettet = opprettet,
                periode = periode,
                epsFormue = epsVerdi,
                søkersFormue = søkerVerdi,
                begrunnelse = null,
                bosituasjon = bosituasjon,
                behandlingsPeriode = periode,
            ),
        ),
    )
}

fun innvilgetFormueVilkår(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode,
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig = Grunnlag.Bosituasjon.Fullstendig.Enslig(
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
        periode = periode,
        begrunnelse = null,
    ),
): Vilkår.Formue.Vurdert {
    val (søkerVerdi, epsVerdi) = when (bosituasjon.harEPS()) {
        true -> {
            Formuegrunnlag.Verdier.empty().copy(verdiEiendommer = 15_000) to
                Formuegrunnlag.Verdier.empty().copy(verdiEiendommer = 15_000)
        }
        false -> {
            Formuegrunnlag.Verdier.empty().copy(verdiEiendommer = 15_000) to
                null
        }
    }
    return Vilkår.Formue.Vurdert.createFromGrunnlag(
        nonEmptyListOf(
            Formuegrunnlag.create(
                id = id,
                opprettet = opprettet,
                periode = periode,
                epsFormue = epsVerdi,
                søkersFormue = søkerVerdi,
                begrunnelse = null,
                bosituasjon = bosituasjon,
                behandlingsPeriode = periode,
            ),
        ),
    )
}
