package no.nav.su.se.bakover.service.skatt

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.sak.Sakstype
import java.time.Year

data class FrioppslagSkattRequest(
    val fnr: Fnr,
    val år: Year,
    val begrunnelse: String,
    //TODO - finn ut hvordan saksnummer formatet for SU-alder er
    val saksnummer: Long,
    val sakstype: Sakstype,
    val saksbehandler: NavIdentBruker.Saksbehandler,
)
