package tilbakekreving.application.service.forhåndsvarsel

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.domain.sak.SakService
import org.slf4j.LoggerFactory
import tilbakekreving.application.service.common.TilbakekrevingsbehandlingTilgangstyringService
import tilbakekreving.domain.forhåndsvarsel.ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand
import tilbakekreving.domain.forhåndsvarsel.ForhåndsvisForhåndsvarselTilbakekrevingsbehandlingCommand
import tilbakekreving.domain.forhåndsvarsel.KunneIkkeForhåndsviseForhåndsvarsel

class ForhåndsvisForhåndsvarselTilbakekrevingsbehandlingService(
    private val tilgangstyring: TilbakekrevingsbehandlingTilgangstyringService,
    private val sakService: SakService,
    private val brevService: BrevService,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun forhåndsvisBrev(
        command: ForhåndsvisForhåndsvarselTilbakekrevingsbehandlingCommand,
    ): Either<KunneIkkeForhåndsviseForhåndsvarsel, PdfA> {
        tilgangstyring.assertHarTilgangTilSak(command.sakId).onLeft {
            return KunneIkkeForhåndsviseForhåndsvarsel.IkkeTilgang(it).left()
        }

        val sak = sakService.hentSak(command.sakId).getOrElse {
            throw IllegalStateException("Kunne ikke vise varsel om tilbakekreving, fant ikke sak. Command: $command")
        }
        if (sak.versjon != command.klientensSisteSaksversjon) {
            log.info("Kunne ikke vise varsel om tilbakekreving. Sakens versjon (${sak.versjon}) er ulik saksbehandlers versjon. Command: $command")
            return KunneIkkeForhåndsviseForhåndsvarsel.UlikVersjon.left()
        }

        return brevService.lagDokument(
            ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand(
                fødselsnummer = sak.fnr,
                saksnummer = sak.saksnummer,
                fritekst = command.fritekst,
                saksbehandler = command.utførtAv,
                correlationId = command.correlationId,
                sakId = command.sakId,
            ),
        )
            .mapLeft { KunneIkkeForhåndsviseForhåndsvarsel.FeilVedDokumentGenerering(it) }
            .map { it.generertDokument }
    }
}
