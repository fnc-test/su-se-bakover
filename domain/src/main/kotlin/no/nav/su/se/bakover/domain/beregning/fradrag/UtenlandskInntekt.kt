package no.nav.su.se.bakover.domain.beregning.fradrag

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right

data class UtenlandskInntekt private constructor(
    val beløpIUtenlandskValuta: Int,
    val valuta: String,
    val kurs: Double
) {
    companion object {
        fun create(beløpIUtenlandskValuta: Int, valuta: String, kurs: Double): UtenlandskInntekt {
            return tryCreate(
                beløpIUtenlandskValuta,
                valuta,
                kurs
            ).getOrHandle { throw IllegalArgumentException(it.toString()) }
        }

        fun tryCreate(
            beløpIUtenlandskValuta: Int,
            valuta: String,
            kurs: Double
        ): Either<UgyldigUtenlandskInntekt, UtenlandskInntekt> {
            if (beløpIUtenlandskValuta < 0) return UgyldigUtenlandskInntekt.BeløpKanIkkeVæreNegativ.left()
            if (valuta.isBlank()) return UgyldigUtenlandskInntekt.ValutaMåFyllesUt.left()
            if (kurs < 0) return UgyldigUtenlandskInntekt.KursKanIkkeVæreNegativ.left()

            return UtenlandskInntekt(beløpIUtenlandskValuta, valuta, kurs).right()
        }
    }

    sealed class UgyldigUtenlandskInntekt {
        object BeløpKanIkkeVæreNegativ : UgyldigUtenlandskInntekt()
        object ValutaMåFyllesUt : UgyldigUtenlandskInntekt()
        object KursKanIkkeVæreNegativ : UgyldigUtenlandskInntekt()
    }
}
