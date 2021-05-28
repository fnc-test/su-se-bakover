package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger

data class IdentifiserSaksbehandlingsutfallSomIkkeStøttes(
    val vilkårsvurderinger: Vilkårsvurderinger,
    val tidligereBeregning: Beregning,
    val nyBeregning: Beregning,
) {
    val resultat: Either<Set<SaksbehandlingsutfallSomIkkeStøttes>, Unit> = VurderOpphørVedRevurdering(vilkårsvurderinger, nyBeregning).resultat.let { opphørVedRevurdering ->
        val utfall = mutableSetOf<SaksbehandlingsutfallSomIkkeStøttes>()
        when (opphørVedRevurdering) {
            is OpphørVedRevurdering.Ja -> {
                if (opphørVedRevurdering.grunn.count() > 1) {
                    utfall.add(SaksbehandlingsutfallSomIkkeStøttes.OpphørAvFlereVilkår)
                }
                if (!opphørVedRevurdering.opphørsdatoErTidligesteDatoIRevurdering()) {
                    utfall.add(SaksbehandlingsutfallSomIkkeStøttes.OpphørErIkkeFraFørsteMåned)
                }
                if (setOf(Opphørsgrunn.SU_UNDER_MINSTEGRENSE).containsAll(opphørVedRevurdering.grunn)) {
                    if (harAndreBeløpsendringerEnnMånederUnderMinstegrense()) {
                        utfall.add(SaksbehandlingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon)
                    }
                    if (!fullstendigOpphør()) {
                        utfall.add(SaksbehandlingsutfallSomIkkeStøttes.DelvisOpphør)
                    }
                }
                if (setOf(Opphørsgrunn.FOR_HØY_INNTEKT).containsAll(opphørVedRevurdering.grunn)) {
                    if (harAndreBeløpsendringerEnnMånederMedBeløp0()) {
                        utfall.add(SaksbehandlingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon)
                    }
                    if (!fullstendigOpphør()) {
                        utfall.add(SaksbehandlingsutfallSomIkkeStøttes.DelvisOpphør)
                    }
                }
                if (setOf(Opphørsgrunn.UFØRHET).containsAll(opphørVedRevurdering.grunn) && harBeløpsendringer(nyBeregning.getMånedsberegninger())) {
                    utfall.add(SaksbehandlingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon)
                }
                if (utfall.isEmpty()) Unit.right() else utfall.left()
            }
            OpphørVedRevurdering.Nei -> Unit.right()
        }
    }

    private fun fullstendigOpphør(): Boolean {
        return nyBeregning.getMånedsberegninger().all { it.erSumYtelseUnderMinstebeløp() } || nyBeregning.getMånedsberegninger().all { it.getSumYtelse() == 0 }
    }

    private fun harAndreBeløpsendringerEnnMånederUnderMinstegrense(): Boolean {
        return harBeløpsendringer(nyBeregning.getMånedsberegninger().filterNot { it.erSumYtelseUnderMinstebeløp() })
    }

    private fun harAndreBeløpsendringerEnnMånederMedBeløp0(): Boolean {
        return harBeløpsendringer(nyBeregning.getMånedsberegninger().filterNot { !it.erSumYtelseUnderMinstebeløp() && it.getSumYtelse() == 0 })
    }

    private fun harBeløpsendringer(nyeMånedsberegninger: List<Månedsberegning>): Boolean {
        return tidligereBeregning.getMånedsberegninger().associate { it.periode to it.getSumYtelse() }.let { tidligereMånederOgBeløp ->
            nyeMånedsberegninger.any { tidligereMånederOgBeløp[it.periode] != it.getSumYtelse() }
        }
    }

    private fun OpphørVedRevurdering.Ja.opphørsdatoErTidligesteDatoIRevurdering(): Boolean {
        return this.opphørsdato == nyBeregning.getMånedsberegninger().minByOrNull { it.periode.fraOgMed }!!.periode.fraOgMed
    }
}

sealed class SaksbehandlingsutfallSomIkkeStøttes {
    object OpphørOgAndreEndringerIKombinasjon : SaksbehandlingsutfallSomIkkeStøttes()
    object OpphørErIkkeFraFørsteMåned : SaksbehandlingsutfallSomIkkeStøttes()
    object DelvisOpphør : SaksbehandlingsutfallSomIkkeStøttes()
    object OpphørAvFlereVilkår : SaksbehandlingsutfallSomIkkeStøttes()
}
