package no.nav.su.se.bakover.web.routes.behandling

import arrow.core.Either
import arrow.core.flatMap
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.response.respond
import io.ktor.response.respondBytes
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.patch
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker.Attestant
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.service.behandling.BehandlingService
import no.nav.su.se.bakover.service.behandling.KunneIkkeIverksetteBehandling
import no.nav.su.se.bakover.service.behandling.KunneIkkeLageBrevutkast
import no.nav.su.se.bakover.service.behandling.KunneIkkeOppretteSøknadsbehandling
import no.nav.su.se.bakover.service.behandling.KunneIkkeUnderkjenneBehandling
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.lesUUID
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.toUUID
import no.nav.su.se.bakover.web.withBehandlingId
import no.nav.su.se.bakover.web.withSakId
import org.slf4j.LoggerFactory
import java.time.LocalDate

internal const val behandlingPath = "$sakPath/{sakId}/behandlinger"

@KtorExperimentalAPI
internal fun Route.behandlingRoutes(
    behandlingService: BehandlingService
) {
    val log = LoggerFactory.getLogger(this::class.java)

    data class OpprettBehandlingBody(val soknadId: String)

    authorize(Brukerrolle.Saksbehandler) {
        post("$sakPath/{sakId}/behandlinger") {
            call.withSakId { sakId ->
                Either.catch { deserialize<OpprettBehandlingBody>(call) }
                    .flatMap { it.soknadId.toUUID() }
                    .fold(
                        ifLeft = { call.svar(BadRequest.message("Ugyldig body")) },
                        ifRight = { søknadId ->
                            behandlingService.opprettSøknadsbehandling(søknadId)
                                .fold(
                                    {
                                        call.svar(
                                            when (it) {
                                                is KunneIkkeOppretteSøknadsbehandling.FantIkkeSøknad -> NotFound.message(
                                                    "Fant ikke søknad med id:$søknadId"
                                                )
                                                is KunneIkkeOppretteSøknadsbehandling.SøknadManglerOppgave -> InternalServerError.message(
                                                    "Søknad med id $søknadId mangler oppgave"
                                                )
                                                is KunneIkkeOppretteSøknadsbehandling.SøknadHarAlleredeBehandling -> BadRequest.message(
                                                    "Søknad med id $søknadId har allerede en behandling"
                                                )
                                                is KunneIkkeOppretteSøknadsbehandling.SøknadErLukket -> BadRequest.message(
                                                    "Søknad med id $søknadId er lukket"
                                                )
                                            }
                                        )
                                    },
                                    {
                                        call.audit("Opprettet behandling på sak: $sakId og søknadId: $søknadId")
                                        call.svar(Created.jsonBody(it))
                                    }
                                )
                        }
                    )
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
        get("$behandlingPath/{behandlingId}") {
            call.withBehandling(behandlingService) {
                call.svar(OK.jsonBody(it))
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        patch("$behandlingPath/{behandlingId}/informasjon") {
            call.withBehandling(behandlingService) { behandling ->
                Either.catch { deserialize<BehandlingsinformasjonJson>(call) }.fold(
                    ifLeft = {
                        log.info("Ugylding behandlingsinformasjon-body", it)
                        call.svar(BadRequest.message("Klarte ikke deserialisere body"))
                    },
                    ifRight = { body ->
                        call.audit("Oppdater behandlingsinformasjon for id: ${behandling.id}")
                        if (body.isValid()) {
                            call.svar(
                                OK.jsonBody(
                                    behandlingService.oppdaterBehandlingsinformasjon(
                                        behandlingId = behandling.id,
                                        behandlingsinformasjon = behandlingsinformasjonFromJson(body)
                                    )
                                )
                            )
                        } else {
                            // TODO (CHM): Her burde vi prøve å logge ut hvilken del av body som ikke er gyldig
                            call.svar(BadRequest.message("Data i behandlingsinformasjon er ugyldig"))
                        }
                    }
                )
            }
        }
    }

    data class OpprettBeregningBody(
        val fraOgMed: LocalDate,
        val tilOgMed: LocalDate,
        val fradrag: List<FradragJson>
    ) {
        fun valid() = fraOgMed.dayOfMonth == 1 &&
            tilOgMed.dayOfMonth == tilOgMed.lengthOfMonth() &&
            fradrag.all {
                Fradragstype.isValid(it.type) &&
                    enumContains<FradragTilhører>(it.tilhører) &&
                    it.utenlandskInntekt?.isValid() ?: true
            }
    }

    data class UnderkjennBody(
        val begrunnelse: String
    ) {
        fun valid() = begrunnelse.isNotBlank()
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$behandlingPath/{behandlingId}/beregn") {
            call.withBehandling(behandlingService) { behandling ->
                Either.catch { deserialize<OpprettBeregningBody>(call) }.fold(
                    ifLeft = {
                        log.info("Ugyldig behandling-body: ", it)
                        call.svar(BadRequest.message("Ugyldig body"))
                    },
                    ifRight = { body ->
                        if (body.valid()) {
                            call.svar(
                                Created.jsonBody(
                                    behandlingService.opprettBeregning(
                                        behandlingId = behandling.id,
                                        fraOgMed = body.fraOgMed,
                                        tilOgMed = body.tilOgMed,
                                        fradrag = body.fradrag.map { it.toFradrag(Periode(body.fraOgMed, body.tilOgMed)) }
                                    )
                                )
                            )
                        } else {
                            call.svar(BadRequest.message("Ugyldige input-parametere for: $body"))
                        }
                    }
                )
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
        get("$behandlingPath/{behandlingId}/utledetSatsInfo") {
            call.withBehandling(behandlingService) { behandling ->
                call.svar(Resultat.json(OK, serialize(behandling.toUtledetSatsInfoJson())))
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
        get("$behandlingPath/{behandlingId}/vedtaksutkast") {
            call.withBehandlingId { behandlingId ->
                behandlingService.lagBrevutkast(behandlingId).fold(
                    {
                        when (it) {
                            KunneIkkeLageBrevutkast.FantIkkeBehandling -> call.respond(InternalServerError.message("Fant ikke behandling"))
                            KunneIkkeLageBrevutkast.KunneIkkeLageBrev -> call.respond(InternalServerError.message("Kunne ikke lage brev"))
                        }
                    },
                    { call.respondBytes(it, ContentType.Application.Pdf) }
                )
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$behandlingPath/{behandlingId}/simuler") {
            call.withBehandling(behandlingService) { behandling ->
                behandlingService.simuler(behandling.id, Saksbehandler(call.suUserContext.getNAVIdent())).fold(
                    {
                        log.info("Feil ved simulering: ", it)
                        call.svar(InternalServerError.message("Kunne ikke gjennomføre simulering"))
                    },
                    { call.svar(OK.jsonBody(it)) }
                )
            }
        }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$behandlingPath/{behandlingId}/tilAttestering") {
            call.withBehandlingId { behandlingId ->
                call.withSakId {
                    val saksBehandler = Saksbehandler(call.suUserContext.getNAVIdent())
                    behandlingService.sendTilAttestering(behandlingId, saksBehandler).fold(
                        {
                            call.svar(InternalServerError.message("Kunne ikke opprette oppgave for attestering"))
                        },
                        {
                            call.audit("Sender behandling med id: ${it.id} til attestering")
                            call.svar(OK.jsonBody(it))
                        }
                    )
                }
            }
        }
    }

    authorize(Brukerrolle.Attestant) {

        fun kunneIkkeIverksetteMelding(feil: KunneIkkeIverksetteBehandling): Resultat {
            // funksjon + return: Triks for å få exhaustive when
            return when (feil) {
                is KunneIkkeIverksetteBehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> Forbidden.message("Attestant og saksbehandler kan ikke være samme person")
                is KunneIkkeIverksetteBehandling.KunneIkkeUtbetale -> InternalServerError.message("Kunne ikke utføre utbetaling")
                is KunneIkkeIverksetteBehandling.KunneIkkeKontrollsimulere -> InternalServerError.message("Kunne ikke utføre kontrollsimulering")
                is KunneIkkeIverksetteBehandling.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte -> InternalServerError.message("Oppdaget inkonsistens mellom tidligere utført simulering og kontrollsimulering. Ny simulering må utføres og kontrolleres før iverksetting kan gjennomføres")
                is KunneIkkeIverksetteBehandling.KunneIkkeJournalføreBrev -> InternalServerError.message("Feil ved journalføring av vedtaksbrev")
                is KunneIkkeIverksetteBehandling.KunneIkkeDistribuereBrev -> InternalServerError.message("Feil ved bestilling av distribusjon for vedtaksbrev")
                is KunneIkkeIverksetteBehandling.FantIkkeBehandling -> NotFound.message("Fant ikke behandling")
                is KunneIkkeIverksetteBehandling.KunneIkkeLukkeOppgave -> InternalServerError.message("Kunne ikke lukke oppgave")
            }
        }

        patch("$behandlingPath/{behandlingId}/iverksett") {
            call.withBehandlingId { behandlingId ->
                call.audit("Iverksetter behandling med id: $behandlingId")
                val navIdent = call.suUserContext.getNAVIdent()

                behandlingService.iverksett(
                    behandlingId = behandlingId,
                    attestant = Attestant(navIdent)
                ).fold(
                    { call.svar(kunneIkkeIverksetteMelding(it)) },
                    { call.svar(OK.jsonBody(it)) }
                )
            }
        }
    }

    authorize(Brukerrolle.Attestant) {
        patch("$behandlingPath/{behandlingId}/underkjenn") {
            val navIdent = call.suUserContext.getNAVIdent()

            call.withBehandlingId { behandlingId ->
                call.audit("behandling med id: $behandlingId godkjennes ikke")
                // TODO jah: Ignorerer resultatet her inntil videre og attesterer uansett.

                Either.catch { deserialize<UnderkjennBody>(call) }.fold(
                    ifLeft = {
                        log.info("Ugyldig behandling-body: ", it)
                        call.svar(BadRequest.message("Ugyldig body"))
                    },
                    ifRight = { body ->
                        if (body.valid()) {
                            behandlingService.underkjenn(
                                behandlingId = behandlingId,
                                attestant = Attestant(navIdent),
                                begrunnelse = body.begrunnelse
                            ).fold(
                                ifLeft = {
                                    fun kunneIkkeUnderkjenneFeilmelding(feil: KunneIkkeUnderkjenneBehandling): Resultat {
                                        return when (feil) {
                                            KunneIkkeUnderkjenneBehandling.FantIkkeBehandling -> NotFound.message("Fant ikke behandling")
                                            KunneIkkeUnderkjenneBehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson -> Forbidden.message(
                                                "Attestant og saksbehandler kan ikke vare samme person."
                                            )
                                            KunneIkkeUnderkjenneBehandling.KunneIkkeOppretteOppgave -> InternalServerError.message(
                                                "Oppgaven er lukket, men vi kunne ikke opprette oppgave. Prøv igjen senere."
                                            )
                                            KunneIkkeUnderkjenneBehandling.FantIkkeAktørId -> InternalServerError.message(
                                                "Fant ikke aktørid som er knyttet til tokenet"
                                            )
                                        }
                                    }
                                    call.svar(kunneIkkeUnderkjenneFeilmelding(it))
                                },
                                ifRight = { call.svar(OK.jsonBody(it)) }
                            )
                        } else {
                            call.svar(BadRequest.message("Må angi en begrunnelse"))
                        }
                    }
                )
            }
        }
    }
}

suspend fun ApplicationCall.withBehandling(
    behandlingService: BehandlingService,
    ifRight: suspend (Behandling) -> Unit
) {
    this.lesUUID("sakId").fold(
        {
            this.svar(BadRequest.message(it))
        },
        { sakId ->
            this.lesUUID("behandlingId").fold(
                {
                    this.svar(BadRequest.message(it))
                },
                { behandlingId ->
                    behandlingService.hentBehandling(behandlingId)
                        .mapLeft { this.svar(NotFound.message("Fant ikke behandling med behandlingId:$behandlingId")) }
                        .map {
                            if (it.sakId == sakId) {
                                this.audit("Hentet behandling med id: $behandlingId")
                                ifRight(it)
                            } else {
                                this.svar(NotFound.message("Ugyldig kombinasjon av sak og behandling"))
                            }
                        }
                }
            )
        }
    )
}
