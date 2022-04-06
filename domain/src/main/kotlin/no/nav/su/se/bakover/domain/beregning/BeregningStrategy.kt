package no.nav.su.se.bakover.domain.beregning

import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.periode.Månedsperiode
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.behandling.Satsgrunn
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.satser.FullSupplerendeStønadFactory
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.satser.Satskategori
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.time.Clock

data class Beregningsperiode(
    private val periode: Periode,
    private val strategy: BeregningStrategy,
) {
    fun strategy(): BeregningStrategy {
        return strategy
    }

    fun periode(): Periode {
        return periode
    }

    fun fradragStrategy(): FradragStrategy {
        return strategy.fradragStrategy()
    }

    fun sats(): Satskategori {
        return strategy.satskategori
    }

    fun månedsoversikt(): Map<Månedsperiode, BeregningStrategy> {
        return periode.tilMånedsperioder().associateWith { strategy }
    }
}

class BeregningStrategyFactory(
    val clock: Clock,
    val satsFactory: SatsFactory
) {
    fun beregn(revurdering: Revurdering): Beregning {
        return beregn(
            grunnlagsdataOgVilkårsvurderinger = revurdering.grunnlagsdataOgVilkårsvurderinger,
            begrunnelse = null,
        )
    }

    fun beregn(søknadsbehandling: Søknadsbehandling, begrunnelse: String?): Beregning {
        return beregn(
            grunnlagsdataOgVilkårsvurderinger = søknadsbehandling.grunnlagsdataOgVilkårsvurderinger,
            begrunnelse = begrunnelse,
        )
    }

    fun beregn(regulering: Regulering, begrunnelse: String?): Beregning {
        return beregn(
            grunnlagsdataOgVilkårsvurderinger = regulering.grunnlagsdataOgVilkårsvurderinger,
            begrunnelse = begrunnelse,
        )
    }

    fun beregn(
        grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger,
        begrunnelse: String?,
    ): Beregning {
        val totalBeregningsperiode = grunnlagsdataOgVilkårsvurderinger.periode()!!

        require(grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.bosituasjon.isNotEmpty()) { "Bosituasjon er påkrevet for å kunne beregne." }

        val delperioder = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.bosituasjon.map {
            Beregningsperiode(
                periode = it.periode,
                strategy = (it as Grunnlag.Bosituasjon.Fullstendig).utledBeregningsstrategi(satsFactory),
            )
        }

        val beregningsgrunnlag = Beregningsgrunnlag.tryCreate(
            beregningsperiode = totalBeregningsperiode,
            uføregrunnlag = when (val vilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger) {
                is Vilkårsvurderinger.Revurdering -> {
                    vilkårsvurderinger.uføre.grunnlag
                }
                is Vilkårsvurderinger.Søknadsbehandling -> {
                    vilkårsvurderinger.uføre.grunnlag
                }
            },
            fradragFraSaksbehandler = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.fradragsgrunnlag,
        ).getOrHandle {
            // TODO jah: Kan vurdere å legge på en left her (KanIkkeBeregne.UgyldigBeregningsgrunnlag
            throw IllegalArgumentException(it.toString())
        }

        require(totalBeregningsperiode.fullstendigOverlapp(delperioder.map { it.periode() }))

        return BeregningFactory(clock).ny(
            fradrag = beregningsgrunnlag.fradrag,
            begrunnelse = begrunnelse,
            beregningsperioder = delperioder,
            satsFactory = satsFactory,
        )
    }
}

sealed class BeregningStrategy {
    abstract fun fradragStrategy(): FradragStrategy
    abstract fun fullSupplerendeStønadFactory(): FullSupplerendeStønadFactory
    abstract fun satsgrunn(): Satsgrunn
    abstract val satskategori: Satskategori

    data class BorAlene(val satsFactory: SatsFactory) : BeregningStrategy() {
        override fun fradragStrategy(): FradragStrategy = FradragStrategy.Enslig
        override fun fullSupplerendeStønadFactory() = satsFactory.fullSupplerendeStønadHøy()
        override fun satsgrunn(): Satsgrunn = Satsgrunn.ENSLIG
        override val satskategori = Satskategori.HØY
    }

    data class BorMedVoksne(val satsFactory: SatsFactory) : BeregningStrategy() {
        override fun fradragStrategy(): FradragStrategy = FradragStrategy.Enslig
        override fun fullSupplerendeStønadFactory() = satsFactory.fullSupplerendeStønadOrdinær()
        override fun satsgrunn(): Satsgrunn = Satsgrunn.DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN
        override val satskategori = Satskategori.ORDINÆR
    }

    data class Eps67EllerEldre(val satsFactory: SatsFactory) : BeregningStrategy() {
        override fun fradragStrategy(): FradragStrategy = FradragStrategy.EpsOver67År
        override fun fullSupplerendeStønadFactory() = satsFactory.fullSupplerendeStønadOrdinær()
        override fun satsgrunn(): Satsgrunn = Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_67_ELLER_ELDRE
        override val satskategori = Satskategori.ORDINÆR
    }

    data class EpsUnder67ÅrOgUførFlyktning(val satsFactory: SatsFactory) : BeregningStrategy() {
        override fun fradragStrategy(): FradragStrategy =
            FradragStrategy.EpsUnder67ÅrOgUførFlyktning(satsFactory.fullSupplerendeStønadOrdinær())

        override fun fullSupplerendeStønadFactory() = satsFactory.fullSupplerendeStønadOrdinær()
        override fun satsgrunn(): Satsgrunn = Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67_UFØR_FLYKTNING
        override val satskategori = Satskategori.ORDINÆR
    }

    data class EpsUnder67År(val satsFactory: SatsFactory) : BeregningStrategy() {
        override fun fradragStrategy(): FradragStrategy = FradragStrategy.EpsUnder67År
        override fun fullSupplerendeStønadFactory() = satsFactory.fullSupplerendeStønadHøy()
        override fun satsgrunn(): Satsgrunn = Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67
        override val satskategori = Satskategori.HØY
    }
}

fun Grunnlag.Bosituasjon.Fullstendig.utledBeregningsstrategi(satsFactory: SatsFactory): BeregningStrategy {
    return when (this) {
        is Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen -> BeregningStrategy.BorMedVoksne(
            satsFactory = satsFactory,
        )
        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning -> BeregningStrategy.EpsUnder67År(
            satsFactory = satsFactory,
        )
        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre -> BeregningStrategy.Eps67EllerEldre(
            satsFactory = satsFactory,
        )
        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning -> BeregningStrategy.EpsUnder67ÅrOgUførFlyktning(
            satsFactory = satsFactory,
        )
        is Grunnlag.Bosituasjon.Fullstendig.Enslig -> BeregningStrategy.BorAlene(satsFactory = satsFactory)
    }
}
