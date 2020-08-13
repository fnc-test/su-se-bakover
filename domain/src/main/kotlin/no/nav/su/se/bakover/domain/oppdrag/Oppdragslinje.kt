package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.now
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class Oppdragslinje(
    val id: UUID = UUID.randomUUID(), // delytelseId,
    val opprettet: Instant = now(),
    val fom: LocalDate,
    val tom: LocalDate,
    val endringskode: Endringskode
) {
    enum class Endringskode {
        NY, ENDR
    }

    override fun equals(other: Any?) = other is Oppdragslinje && other.fom == fom && other.tom == tom && other.endringskode == endringskode
}
