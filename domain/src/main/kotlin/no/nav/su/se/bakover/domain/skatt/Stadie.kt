package no.nav.su.se.bakover.domain.skatt

/**
 * De 2 ulike stadiene for et spesifisert summert skattegrunnlag
 * Spesifitets rekkefølge er oppgjør > utkast
 */
enum class Stadie(val verdi: String) {
    UTKAST("utkast"),
    OPPGJØR("oppgjoer"),
}
