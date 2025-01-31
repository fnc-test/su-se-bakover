package no.nav.su.se.bakover.test

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.FamiliegjenforeningVilkår
import no.nav.su.se.bakover.domain.vilkår.FastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.FlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.FormueVilkår
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.LovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.PensjonsVilkår
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.test.vilkår.avslåttFormueVilkår
import no.nav.su.se.bakover.test.vilkår.familiegjenforeningVilkårAvslag
import no.nav.su.se.bakover.test.vilkår.familiegjenforeningVilkårInnvilget
import no.nav.su.se.bakover.test.vilkår.fastOppholdVilkårAvslag
import no.nav.su.se.bakover.test.vilkår.fastOppholdVilkårInnvilget
import no.nav.su.se.bakover.test.vilkår.flyktningVilkårAvslått
import no.nav.su.se.bakover.test.vilkår.flyktningVilkårInnvilget
import no.nav.su.se.bakover.test.vilkår.formuevilkårAvslåttPgaBrukersformue
import no.nav.su.se.bakover.test.vilkår.formuevilkårUtenEps0Innvilget
import no.nav.su.se.bakover.test.vilkår.innvilgetFormueVilkår
import no.nav.su.se.bakover.test.vilkår.institusjonsoppholdvilkårAvslag
import no.nav.su.se.bakover.test.vilkår.institusjonsoppholdvilkårInnvilget
import no.nav.su.se.bakover.test.vilkår.lovligOppholdVilkårAvslag
import no.nav.su.se.bakover.test.vilkår.lovligOppholdVilkårInnvilget
import no.nav.su.se.bakover.test.vilkår.pensjonsVilkårAvslag
import no.nav.su.se.bakover.test.vilkår.pensjonsVilkårInnvilget
import no.nav.su.se.bakover.test.vilkår.tilstrekkeligDokumentert
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdAvslag
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdInnvilget
import no.nav.su.se.bakover.test.vilkår.utilstrekkeligDokumentert
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkårForventetInntekt0
import no.nav.su.se.bakover.test.vurderingsperiode.vurderingsperiodeLovligOppholdAvslag
import no.nav.su.se.bakover.test.vurderingsperiode.vurderingsperiodeLovligOppholdInnvilget
import vilkår.personligOppmøtevilkårAvslag
import vilkår.personligOppmøtevilkårInnvilget
import vurderingsperiode.vurderingsperiodeFamiliegjenforeningInnvilget
import java.util.UUID

fun vilkårsvurderingSøknadsbehandlingIkkeVurdert(): Vilkårsvurderinger.Søknadsbehandling.Uføre {
    return Vilkårsvurderinger.Søknadsbehandling.Uføre.ikkeVurdert()
}

fun vilkårsvurderingSøknadsbehandlingIkkeVurdertAlder() = Vilkårsvurderinger.Søknadsbehandling.Alder(
    formue = FormueVilkår.IkkeVurdert,
    lovligOpphold = LovligOppholdVilkår.IkkeVurdert,
    fastOpphold = FastOppholdINorgeVilkår.IkkeVurdert,
    institusjonsopphold = InstitusjonsoppholdVilkår.IkkeVurdert,
    utenlandsopphold = UtenlandsoppholdVilkår.IkkeVurdert,
    personligOppmøte = PersonligOppmøteVilkår.IkkeVurdert,
    opplysningsplikt = OpplysningspliktVilkår.IkkeVurdert,
    pensjon = PensjonsVilkår.IkkeVurdert,
    familiegjenforening = FamiliegjenforeningVilkår.IkkeVurdert,
)

fun vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder() = Vilkårsvurderinger.Søknadsbehandling.Alder(
    formue = innvilgetFormueVilkår(),
    lovligOpphold = lovligOppholdVilkårInnvilget(),
    fastOpphold = fastOppholdVilkårInnvilget(),
    institusjonsopphold = institusjonsoppholdvilkårInnvilget(),
    utenlandsopphold = utenlandsoppholdInnvilget(),
    personligOppmøte = personligOppmøtevilkårInnvilget(),
    opplysningsplikt = tilstrekkeligDokumentert(),
    familiegjenforening = familiegjenforeningVilkårInnvilget(),
    pensjon = pensjonsVilkårInnvilget(),
)

fun vilkårsvurderingSøknadsbehandlingVurdertAvslagAlder() = Vilkårsvurderinger.Søknadsbehandling.Alder(
    formue = avslåttFormueVilkår(),
    lovligOpphold = lovligOppholdVilkårAvslag(),
    fastOpphold = fastOppholdVilkårAvslag(),
    institusjonsopphold = institusjonsoppholdvilkårAvslag(),
    utenlandsopphold = utenlandsoppholdAvslag(),
    personligOppmøte = personligOppmøtevilkårAvslag(),
    opplysningsplikt = utilstrekkeligDokumentert(),
    familiegjenforening = familiegjenforeningVilkårAvslag(),
    pensjon = pensjonsVilkårAvslag(),
)

fun vilkårsvurderingRevurderingIkkeVurdert() = Vilkårsvurderinger.Revurdering.Uføre.ikkeVurdert()

/**
 * periode: 2021
 * uføre: innvilget med forventet inntekt 0
 * bosituasjon: enslig
 * formue: ingen eps, sum 0
 * utenlandsopphold: innvilget
 */
fun vilkårsvurderingerSøknadsbehandlingInnvilget(
    periode: Periode = år(2021),
    uføre: UføreVilkår = innvilgetUførevilkårForventetInntekt0(
        id = UUID.randomUUID(),
        periode = periode,
    ),
    lovligOppholdVilkår: LovligOppholdVilkår = lovligOppholdVilkårInnvilget(
        nonEmptyListOf(vurderingsperiodeLovligOppholdInnvilget(vurderingsperiode = periode)),
    ),
    bosituasjon: NonEmptyList<Grunnlag.Bosituasjon.Fullstendig> = nonEmptyListOf(
        bosituasjongrunnlagEnslig(
            id = UUID.randomUUID(),
            periode = periode,
        ),
    ),
    utenlandsopphold: UtenlandsoppholdVilkår = utenlandsoppholdInnvilget(
        id = UUID.randomUUID(),
        periode = periode,
    ),
    opplysningsplikt: OpplysningspliktVilkår = tilstrekkeligDokumentert(
        id = UUID.randomUUID(),
        periode = periode,
    ),
    formue: FormueVilkår = formuevilkårUtenEps0Innvilget(periode = periode, bosituasjon = bosituasjon),
    flyktning: FlyktningVilkår = flyktningVilkårInnvilget(periode = periode),
    fastOpphold: FastOppholdINorgeVilkår = fastOppholdVilkårInnvilget(periode = periode),
    personligOppmøte: PersonligOppmøteVilkår = personligOppmøtevilkårInnvilget(periode = periode),
    institusjonsopphold: InstitusjonsoppholdVilkår = institusjonsoppholdvilkårInnvilget(periode = periode),
): Vilkårsvurderinger.Søknadsbehandling.Uføre {
    return Vilkårsvurderinger.Søknadsbehandling.Uføre(
        uføre = uføre,
        utenlandsopphold = utenlandsopphold,
        formue = formue,
        opplysningsplikt = opplysningsplikt,
        lovligOpphold = lovligOppholdVilkår,
        fastOpphold = fastOpphold,
        institusjonsopphold = institusjonsopphold,
        personligOppmøte = personligOppmøte,
        flyktning = flyktning,
    )
}

fun vilkårsvurderingerRevurderingInnvilget(
    periode: Periode = år(2021),
    uføre: UføreVilkår = innvilgetUførevilkårForventetInntekt0(periode = periode),
    bosituasjon: NonEmptyList<Grunnlag.Bosituasjon.Fullstendig> = nonEmptyListOf(bosituasjongrunnlagEnslig(periode = periode)),
    lovligOpphold: LovligOppholdVilkår = lovligOppholdVilkårInnvilget(
        nonEmptyListOf(vurderingsperiodeLovligOppholdInnvilget(vurderingsperiode = periode)),
    ),
    formue: FormueVilkår = formuevilkårUtenEps0Innvilget(periode = periode, bosituasjon = bosituasjon),
    utenlandsopphold: UtenlandsoppholdVilkår = utenlandsoppholdInnvilget(periode = periode),
    opplysningsplikt: OpplysningspliktVilkår = tilstrekkeligDokumentert(periode = periode),
    flyktningVilkår: FlyktningVilkår = flyktningVilkårInnvilget(periode = periode),
    fastOpphold: FastOppholdINorgeVilkår = fastOppholdVilkårInnvilget(periode = periode),
    personligOppmøte: PersonligOppmøteVilkår = personligOppmøtevilkårInnvilget(periode = periode),
    institusjonsopphold: InstitusjonsoppholdVilkår = institusjonsoppholdvilkårInnvilget(periode = periode),
): Vilkårsvurderinger.Revurdering.Uføre {
    return Vilkårsvurderinger.Revurdering.Uføre(
        uføre = uføre,
        lovligOpphold = lovligOpphold,
        formue = formue,
        utenlandsopphold = utenlandsopphold,
        opplysningsplikt = opplysningsplikt,
        flyktning = flyktningVilkår,
        fastOpphold = fastOpphold,
        personligOppmøte = personligOppmøte,
        institusjonsopphold = institusjonsopphold,
    )
}

fun vilkårsvurderingerAvslåttAlleRevurdering(
    periode: Periode = år(2021),
    uføre: UføreVilkår = avslåttUførevilkårUtenGrunnlag(periode = periode),
    bosituasjon: NonEmptyList<Grunnlag.Bosituasjon.Fullstendig> = nonEmptyListOf(bosituasjongrunnlagEnslig(periode = periode)),
    lovligOpphold: LovligOppholdVilkår = lovligOppholdVilkårAvslag(
        nonEmptyListOf(vurderingsperiodeLovligOppholdAvslag(vurderingsperiode = periode)),
    ),
    formue: FormueVilkår = formuevilkårAvslåttPgaBrukersformue(
        periode = periode,
        bosituasjon = bosituasjon.toList().toNonEmptyList(),
    ),
    utenlandsopphold: UtenlandsoppholdVilkår = utenlandsoppholdAvslag(periode = periode),
    opplysningsplikt: OpplysningspliktVilkår = utilstrekkeligDokumentert(periode = periode),
    flyktningVilkår: FlyktningVilkår = flyktningVilkårAvslått(periode = periode),
    fastOpphold: FastOppholdINorgeVilkår = fastOppholdVilkårAvslag(periode = periode),
    personligOppmøte: PersonligOppmøteVilkår = personligOppmøtevilkårAvslag(periode = periode),
    institusjonsopphold: InstitusjonsoppholdVilkår = institusjonsoppholdvilkårAvslag(periode = periode),
): Vilkårsvurderinger.Revurdering.Uføre {
    return Vilkårsvurderinger.Revurdering.Uføre(
        uføre = uføre,
        lovligOpphold = lovligOpphold,
        formue = formue,
        utenlandsopphold = utenlandsopphold,
        opplysningsplikt = opplysningsplikt,
        flyktning = flyktningVilkår,
        fastOpphold = fastOpphold,
        personligOppmøte = personligOppmøte,
        institusjonsopphold = institusjonsopphold,
    )
}

/**
 * Denne er ikke reell, da man i praksis ikke kan avslå alt samtidig, og bør brukes i minst mulig grad.
 *
 * Defaults:
 * periode: 2021
 * bosituasjon: enslig
 *
 * Predefined:
 * uføre: avslag
 * formue: innvilget
 */
fun vilkårsvurderingerAvslåttAlle(
    periode: Periode = år(2021),
): Vilkårsvurderinger.Søknadsbehandling.Uføre {
    return Vilkårsvurderinger.Søknadsbehandling.Uføre(
        uføre = avslåttUførevilkårUtenGrunnlag(
            periode = periode,
        ),
        utenlandsopphold = utenlandsoppholdAvslag(periode = periode),
        formue = formuevilkårAvslåttPgaBrukersformue(
            periode = periode,
            bosituasjon = bosituasjongrunnlagEnslig(periode = periode),
        ),
        opplysningsplikt = utilstrekkeligDokumentert(periode = periode),
        lovligOpphold = lovligOppholdVilkårAvslag(),
        fastOpphold = fastOppholdVilkårAvslag(periode = periode),
        personligOppmøte = personligOppmøtevilkårAvslag(periode = periode),
        flyktning = flyktningVilkårAvslått(periode = periode),
        institusjonsopphold = institusjonsoppholdvilkårAvslag(periode = periode),
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
    bosituasjon: NonEmptyList<Grunnlag.Bosituasjon.Fullstendig> = nonEmptyListOf(bosituasjongrunnlagEnslig(periode = periode)),
): Vilkårsvurderinger.Revurdering.Uføre {
    return Vilkårsvurderinger.Revurdering.Uføre(
        uføre = avslåttUførevilkårUtenGrunnlag(periode = periode),
        lovligOpphold = lovligOppholdVilkårInnvilget(
            nonEmptyListOf(vurderingsperiodeLovligOppholdInnvilget(vurderingsperiode = periode)),
        ),
        formue = formuevilkårUtenEps0Innvilget(
            periode = periode,
            bosituasjon = bosituasjon,
        ),
        utenlandsopphold = utenlandsoppholdInnvilget(periode = periode),
        opplysningsplikt = tilstrekkeligDokumentert(periode = periode),
        flyktning = flyktningVilkårInnvilget(periode = periode),
        fastOpphold = fastOppholdVilkårInnvilget(periode = periode),
        personligOppmøte = personligOppmøtevilkårInnvilget(periode = periode),
        institusjonsopphold = institusjonsoppholdvilkårInnvilget(periode = periode),
    )
}

fun vilkårsvurderingerAlderInnvilget(
    stønadsperiode: Stønadsperiode = stønadsperiode2021,
    lovligOpphold: LovligOppholdVilkår = lovligOppholdVilkårInnvilget(),
    bosituasjon: NonEmptyList<Grunnlag.Bosituasjon.Fullstendig> = nonEmptyListOf(
        bosituasjongrunnlagEnslig(
            id = UUID.randomUUID(),
            periode = stønadsperiode.periode,
        ),
    ),
    utenlandsopphold: UtenlandsoppholdVilkår = utenlandsoppholdInnvilget(
        id = UUID.randomUUID(),
        periode = stønadsperiode.periode,
    ),
    opplysningsplikt: OpplysningspliktVilkår = tilstrekkeligDokumentert(
        id = UUID.randomUUID(),
        periode = stønadsperiode.periode,
    ),
    formue: FormueVilkår = formuevilkårUtenEps0Innvilget(
        periode = stønadsperiode.periode,
        bosituasjon = bosituasjon,
    ),
    pensjon: PensjonsVilkår = pensjonsVilkårInnvilget(
        periode = stønadsperiode.periode,
    ),
    familiegjenforeningVilkår: FamiliegjenforeningVilkår = familiegjenforeningVilkårInnvilget(
        vurderingsperioder = nonEmptyListOf(vurderingsperiodeFamiliegjenforeningInnvilget(periode = stønadsperiode.periode)),
    ),
    fastOpphold: FastOppholdINorgeVilkår = fastOppholdVilkårInnvilget(
        periode = stønadsperiode.periode,
    ),
    personligOppmøte: PersonligOppmøteVilkår = personligOppmøtevilkårInnvilget(periode = stønadsperiode.periode),
    institusjonsopphold: InstitusjonsoppholdVilkår = institusjonsoppholdvilkårInnvilget(periode = stønadsperiode.periode),
): Vilkårsvurderinger.Søknadsbehandling.Alder {
    return Vilkårsvurderinger.Søknadsbehandling.Alder(
        utenlandsopphold = utenlandsopphold,
        formue = formue,
        opplysningsplikt = opplysningsplikt,
        lovligOpphold = lovligOpphold,
        fastOpphold = fastOpphold,
        institusjonsopphold = institusjonsopphold,
        personligOppmøte = personligOppmøte,
        pensjon = pensjon,
        familiegjenforening = familiegjenforeningVilkår,
    )
}
