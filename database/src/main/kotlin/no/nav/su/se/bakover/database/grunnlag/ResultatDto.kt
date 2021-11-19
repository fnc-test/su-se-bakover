package no.nav.su.se.bakover.database.grunnlag

import no.nav.su.se.bakover.domain.vilkår.Resultat

internal enum class ResultatDto {
    AVSLAG,
    INNVILGET,
    UAVKLART;

    fun toDomain() = when (this) {
        AVSLAG -> Resultat.Avslag
        INNVILGET -> Resultat.Innvilget
        UAVKLART -> Resultat.Uavklart
    }
}

internal fun Resultat.toDto() = when (this) {
    Resultat.Avslag -> ResultatDto.AVSLAG
    Resultat.Innvilget -> ResultatDto.INNVILGET
    Resultat.Uavklart -> ResultatDto.UAVKLART
}