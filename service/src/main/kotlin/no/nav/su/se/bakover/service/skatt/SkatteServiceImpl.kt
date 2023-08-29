package no.nav.su.se.bakover.service.skatt

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.JournalpostSkatt.Companion.lagJournalpostIkkeTilknyttetSak
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.YearRange
import no.nav.su.se.bakover.common.tid.toRange
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.skatt.KunneIkkeHenteSkattemelding
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import no.nav.su.se.bakover.domain.skatt.Skatteoppslag
import java.time.Clock
import java.time.Year
import java.util.UUID

class SkatteServiceImpl(
    private val skatteClient: Skatteoppslag,
    private val dokArkiv: DokArkiv,
    private val skattDokumentService: SkattDokumentService,
    private val personService: PersonService,
    private val sessionFactory: SessionFactory,
    val clock: Clock,
) : SkatteService {

    override fun hentSamletSkattegrunnlag(
        fnr: Fnr,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Skattegrunnlag = Skattegrunnlag(
        id = UUID.randomUUID(),
        fnr = fnr,
        hentetTidspunkt = Tidspunkt.now(clock),
        saksbehandler = saksbehandler,
        årsgrunnlag = skatteClient.hentSamletSkattegrunnlag(fnr, Year.now(clock).minusYears(1))
            .hentMestGyldigeSkattegrunnlag(),
        årSpurtFor = Year.now(clock).minusYears(1).toRange(),
    )

    override fun hentSamletSkattegrunnlagForÅr(
        fnr: Fnr,
        saksbehandler: NavIdentBruker.Saksbehandler,
        yearRange: YearRange,
    ): Skattegrunnlag = Skattegrunnlag(
        id = UUID.randomUUID(),
        fnr = fnr,
        hentetTidspunkt = Tidspunkt.now(clock),
        saksbehandler = saksbehandler,
        årsgrunnlag = skatteClient.hentSamletSkattegrunnlagForÅrsperiode(fnr, yearRange)
            .map { it.hentMestGyldigeSkattegrunnlag() }.toNonEmptyList(),
        årSpurtFor = yearRange,
    )

    override fun hentOgLagPdfAvSamletSkattegrunnlagFor(
        request: FrioppslagSkattRequest,
    ): Either<KunneIkkeHenteSkattemelding, PdfA> {
        val skattegrunnlag = Skattegrunnlag(
            id = UUID.randomUUID(),
            fnr = request.fnr,
            hentetTidspunkt = Tidspunkt.now(clock),
            saksbehandler = request.saksbehandler,
            årsgrunnlag = skatteClient.hentSamletSkattegrunnlag(request.fnr, request.år).hentMestGyldigeSkattegrunnlagEllerFeil()
                .getOrElse { return it.left() },
            årSpurtFor = request.år.toRange(),
        )

        val skatteDokument = skattDokumentService.generer(skattegrunnlag).getOrElse {
            TODO()
        }

        skatteDokument.lagJournalpostIkkeTilknyttetSak(
            person = personService.hentPersonMedSystembruker(request.fnr).getOrElse {
                TODO()
            },
            saksnummer = request.saksnummer,
            sakstype = request.sakstype,
        )


    }
}
