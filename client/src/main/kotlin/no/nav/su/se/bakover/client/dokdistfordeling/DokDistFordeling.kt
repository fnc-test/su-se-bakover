package no.nav.su.se.bakover.client.dokdistfordeling

import arrow.core.Either
import dokument.domain.Distribusjonstidspunkt
import dokument.domain.Distribusjonstype
import dokument.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.common.journal.JournalpostId

interface DokDistFordeling {
    fun bestillDistribusjon(
        journalPostId: JournalpostId,
        distribusjonstype: Distribusjonstype,
        distribusjonstidspunkt: Distribusjonstidspunkt,
    ): Either<KunneIkkeBestilleDistribusjon, BrevbestillingId>
}

data object KunneIkkeBestilleDistribusjon
