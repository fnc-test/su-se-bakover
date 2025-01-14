package no.nav.su.se.bakover.domain.vilkår

import no.nav.su.se.bakover.common.tid.periode.Periode
import java.time.LocalDate

sealed interface IkkeVurdertVilkår : Vilkår {
    override val vurdering: Vurdering.Uavklart get() = Vurdering.Uavklart
    override val erAvslag: Boolean get() = false
    override val erInnvilget: Boolean get() = false
    override val perioder: List<Periode> get() = emptyList()

    override fun hentTidligesteDatoForAvslag(): LocalDate? = null
}
