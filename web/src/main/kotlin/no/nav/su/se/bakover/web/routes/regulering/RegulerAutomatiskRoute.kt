package no.nav.su.se.bakover.web.routes.regulering

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import arrow.core.separateEither
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.service.regulering.ReguleringService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.lesUUID
import no.nav.su.se.bakover.web.routes.grunnlag.UføregrunnlagJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragJson
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import java.time.LocalDate
import java.util.UUID

internal fun Route.reguler(
    reguleringService: ReguleringService,
) {
    /*
    * Automatisk regulering av alle saker kan kun startes fra driftssiden
    **/
    authorize(Brukerrolle.Drift) {
        post("$reguleringPath/automatisk") {
            data class Request(val startDato: LocalDate)
            call.withBody<Request> {
                CoroutineScope(Dispatchers.IO).launch {
                    reguleringService.startRegulering(it.startDato)
                }
                call.svar(Resultat.okJson(HttpStatusCode.OK))
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$reguleringPath/manuell/{reguleringId}") {
            data class Body(val fradrag: List<FradragJson>, val uføre: List<UføregrunnlagJson>)

            call.lesUUID("reguleringId").fold(
                ifLeft = {
                    HttpStatusCode.BadRequest.errorJson(it, "reguleringId_mangler_eller_feil_format")
                },
                ifRight = { id ->
                    call.withBody<Body> { body ->
                        reguleringService.regulerManuelt(
                            reguleringId = id,
                            uføregrunnlag = body.uføre.toDomain(), // TODO ai: asap
                            fradrag = body.fradrag.toDomain().getOrHandle { return@post call.svar(it) },
                            saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent)
                        )

                        call.svar(Resultat.okJson(HttpStatusCode.OK))
                    }
                },
            )
        }
    }
}

private fun List<FradragJson>.toDomain(): Either<Resultat, List<Grunnlag.Fradragsgrunnlag>> {
    val (resultat, f) = this
        .map { it.toFradrag() }
        .separateEither()

    if (resultat.isNotEmpty()) return resultat.first().left()

    return f.map {
        Grunnlag.Fradragsgrunnlag.tryCreate(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            fradrag = it,
        ).getOrHandle {
            return HttpStatusCode.BadRequest.errorJson(
                message = "Kunne ikke lage fradrag",
                code = "kunne_ikke_lage_fradrag",
            ).left()
        }
    }.right()
}

private fun List<UføregrunnlagJson>.toDomain(): List<Grunnlag.Uføregrunnlag> {
    return this.map {
        Grunnlag.Uføregrunnlag(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(), // todo ai: legg til clock
            periode = it.periode.toPeriode().getOrHandle { throw IllegalStateException("") },
            uføregrad = Uføregrad.tryParse(it.uføregrad).getOrHandle { throw IllegalStateException("") },
            forventetInntekt = it.forventetInntekt
        )
    }
}
