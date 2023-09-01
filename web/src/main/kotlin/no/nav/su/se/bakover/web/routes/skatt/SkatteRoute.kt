package no.nav.su.se.bakover.web.routes.skatt

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.web.ErrorJson
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withFnr
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.skatt.KunneIkkeHenteSkattemelding
import no.nav.su.se.bakover.service.skatt.FrioppslagSkattRequest
import no.nav.su.se.bakover.service.skatt.KunneIkkeHenteOgLagePdfAvSkattegrunnlag
import no.nav.su.se.bakover.service.skatt.SkatteService
import no.nav.su.se.bakover.web.routes.person.tilResultat
import java.time.Year

internal const val skattPath = "/skatt"

internal fun Route.skattRoutes(skatteService: SkatteService) {
    data class FrioppslagRequestBody(
        val år: Int,
        val begrunnelse: String,
    ) {
        fun tilFrioppslagSkattRequest(
            fnr: Fnr,
            saksbehandler: NavIdentBruker.Saksbehandler,
        ): FrioppslagSkattRequest = FrioppslagSkattRequest(
            fnr = fnr,
            år = Year.of(år),
            begrunnelse = begrunnelse,
            saksbehandler = saksbehandler,
        )
    }

    get("$skattPath/person/{fnr}") {
        call.withFnr { fnr ->
            call.withBody<FrioppslagRequestBody> { body ->
                skatteService.hentOgLagPdfAvSamletSkattegrunnlagFor(
                    request = body.tilFrioppslagSkattRequest(fnr, call.suUserContext.saksbehandler),
                ).fold(
                    ifLeft = { call.svar(it.tilResultat()) },
                    ifRight = {
                        call.audit(fnr, AuditLogEvent.Action.SEARCH, null)
                        call.respondBytes(it.getContent(), ContentType.Application.Pdf)
                    },
                )
            }
        }
    }
}

internal fun KunneIkkeHenteOgLagePdfAvSkattegrunnlag.tilResultat(): Resultat {
    return when (this) {
        is KunneIkkeHenteOgLagePdfAvSkattegrunnlag.FeilVedHentingAvPerson -> this.originalFeil.tilResultat()
        is KunneIkkeHenteOgLagePdfAvSkattegrunnlag.FeilVedPdfGenerering -> ErrorJson(
            "Feil ved generering av pdf",
            "feil_ved_generering_av_pdf",
        ).tilResultat(HttpStatusCode.InternalServerError)

        is KunneIkkeHenteOgLagePdfAvSkattegrunnlag.KunneIkkeHenteSkattemelding -> this.originalFeil.tilResultat()
    }
}

internal fun KunneIkkeHenteSkattemelding.tilResultat(): Resultat = when (this) {
    KunneIkkeHenteSkattemelding.FinnesIkke -> this.tilErrorJson().tilResultat(HttpStatusCode.NotFound)
    KunneIkkeHenteSkattemelding.ManglerRettigheter -> this.tilErrorJson().tilResultat(HttpStatusCode.Forbidden)
    KunneIkkeHenteSkattemelding.Nettverksfeil -> this.tilErrorJson().tilResultat(HttpStatusCode.InternalServerError)
    KunneIkkeHenteSkattemelding.PersonFeil -> this.tilErrorJson().tilResultat(HttpStatusCode.InternalServerError)
    KunneIkkeHenteSkattemelding.UkjentFeil -> this.tilErrorJson().tilResultat(HttpStatusCode.InternalServerError)
}

internal fun KunneIkkeHenteSkattemelding.tilErrorJson(): ErrorJson = when (this) {
    is KunneIkkeHenteSkattemelding.FinnesIkke -> ErrorJson(
        "Ingen summert skattegrunnlag funnet på oppgitt fødselsnummer og inntektsår",
        "ingen_skattegrunnlag_for_gitt_fnr_og_år",
    )

    KunneIkkeHenteSkattemelding.ManglerRettigheter -> ErrorJson(
        "Autentiserings- eller autoriseringsfeil mot Sigrun/Skatteetaten. Mangler bruker noen rettigheter?",
        "mangler_rettigheter_mot_skatt",
    )

    KunneIkkeHenteSkattemelding.Nettverksfeil -> ErrorJson(
        "Får ikke kontakt med Sigrun/Skatteetaten. Prøv igjen senere.",
        "nettverksfeil_skatt",
    )

    KunneIkkeHenteSkattemelding.PersonFeil -> ErrorJson(
        "Personfeil ved oppslag",
        "feil_ved_oppslag_person",
    )

    KunneIkkeHenteSkattemelding.UkjentFeil -> ErrorJson(
        "Uforventet feil oppstod ved kall til Sigrun/Skatteetaten.",
        "uforventet_feil_mot_skatt",
    )
}
