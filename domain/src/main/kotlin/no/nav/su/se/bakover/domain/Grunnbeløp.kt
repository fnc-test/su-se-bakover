package no.nav.su.se.bakover.domain

import java.time.LocalDate
import java.time.Month

class Grunnbeløp private constructor(private val multiplier: Double) {
    private val datoToBeløp: Map<LocalDate, Int> = mapOf(
        LocalDate.of(2017, Month.MAY, 1) to 93634,
        LocalDate.of(2018, Month.MAY, 1) to 96883,
        LocalDate.of(2019, Month.MAY, 1) to 99858,
        LocalDate.of(2020, Month.MAY, 1) to 101351
    )

    fun fraDato(dato: LocalDate): Double = datoToBeløp.entries
        .sortedByDescending { it.key }
        .first { dato.isAfter(it.key) || dato.isEqual(it.key) }.value * multiplier

    companion object {
        val `2,28G` = Grunnbeløp(2.28)
        val `2,48G` = Grunnbeløp(2.48)
        val `1G` = Grunnbeløp(1.0)
        val `0,5G` = Grunnbeløp(0.5)
    }
}
