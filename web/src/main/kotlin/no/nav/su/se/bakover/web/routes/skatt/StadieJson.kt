package no.nav.su.se.bakover.web.routes.skatt

import arrow.core.Either
import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.infrastructure.web.ErrorJson
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.skatt.SamletSkattegrunnlagForÅrOgStadie
import no.nav.su.se.bakover.domain.skatt.Stadie
import no.nav.su.se.bakover.web.routes.skatt.SkattegrunnlagForÅrJson.Companion.toJson

internal sealed interface StadieJson {
    val inntektsår: Int

    data class Grunnlag(
        val grunnlag: SkattegrunnlagForÅrJson,
        val stadie: Stadie,
        override val inntektsår: Int,
    ) : StadieJson

    data class Feil(val error: ErrorJson, override val inntektsår: Int) : StadieJson

    companion object {
        fun NonEmptyList<SamletSkattegrunnlagForÅrOgStadie>.toJson(): NonEmptyList<StadieJson> {
            return this.map { skattegrunnlagForÅr ->

                when (val oppslag = skattegrunnlagForÅr.oppslag) {
                    is Either.Left -> Feil(
                        error = oppslag.value.tilErrorJson(),
                        inntektsår = skattegrunnlagForÅr.inntektsår.value,
                    )

                    is Either.Right -> Grunnlag(
                        grunnlag = oppslag.value.toJson(),
                        stadie = when (skattegrunnlagForÅr) {
                            is SamletSkattegrunnlagForÅrOgStadie.Oppgjør -> Stadie.OPPGJØR
                            is SamletSkattegrunnlagForÅrOgStadie.Utkast -> Stadie.UTKAST
                        },
                        inntektsår = skattegrunnlagForÅr.inntektsår.value,
                    )
                }
            }.toNonEmptyList()
        }
    }
}