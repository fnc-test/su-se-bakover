package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.common.periode.PeriodeJson
import no.nav.su.se.bakover.common.periode.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag

internal data class UføregrunnlagJson(
    val id: String,
    val opprettet: String,
    val periode: PeriodeJson,
    val uføregrad: Int,
    val forventetInntekt: Int,
)

internal fun Grunnlag.Uføregrunnlag.toJson() = UføregrunnlagJson(
    id = this.id.toString(),
    opprettet = this.opprettet.toString(),
    periode = this.periode.toJson(),
    uføregrad = this.uføregrad.value,
    forventetInntekt = this.forventetInntekt,
)

internal fun List<Grunnlag.Uføregrunnlag>.toJson() = this.map {
    it.toJson()
}
