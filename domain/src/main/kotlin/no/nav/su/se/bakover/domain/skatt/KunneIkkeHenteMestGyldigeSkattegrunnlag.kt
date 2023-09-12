package no.nav.su.se.bakover.domain.skatt

import java.lang.IllegalStateException

sealed interface KunneIkkeHenteMestGyldigeSkattegrunnlag {
    data object Nettverksfeil : KunneIkkeHenteMestGyldigeSkattegrunnlag
    data object UkjentFeil : KunneIkkeHenteMestGyldigeSkattegrunnlag
    data object ManglerRettigheter : KunneIkkeHenteMestGyldigeSkattegrunnlag
    data object PersonFeil : KunneIkkeHenteMestGyldigeSkattegrunnlag
    data object OppslagetInneholdtUyldigData : KunneIkkeHenteMestGyldigeSkattegrunnlag

    fun tilKunneIkkeHenteSkattemelding(): KunneIkkeHenteSkattemelding = when (this) {
        ManglerRettigheter -> KunneIkkeHenteSkattemelding.ManglerRettigheter
        Nettverksfeil -> KunneIkkeHenteSkattemelding.Nettverksfeil
        PersonFeil -> KunneIkkeHenteSkattemelding.PersonFeil
        UkjentFeil -> KunneIkkeHenteSkattemelding.UkjentFeil
        OppslagetInneholdtUyldigData -> KunneIkkeHenteSkattemelding.OppslagetInneholdtUgyldigData
    }

    companion object {
        fun KunneIkkeHenteSkattemelding.tilKunneIkkeHenteMestGyldigeSkattegrunnlag(): KunneIkkeHenteMestGyldigeSkattegrunnlag =
            when (this) {
                KunneIkkeHenteSkattemelding.FinnesIkke -> throw IllegalStateException("At et skattegrunnlag ikke finnes, regnes som oftest som 'gyldig' fordi man vil gjøre noe videre med den")
                KunneIkkeHenteSkattemelding.ManglerRettigheter -> ManglerRettigheter
                KunneIkkeHenteSkattemelding.Nettverksfeil -> Nettverksfeil
                KunneIkkeHenteSkattemelding.PersonFeil -> PersonFeil
                KunneIkkeHenteSkattemelding.UkjentFeil -> UkjentFeil
                KunneIkkeHenteSkattemelding.OppslagetInneholdtUgyldigData -> OppslagetInneholdtUyldigData
            }
    }
}
