package no.nav.su.se.bakover.database.grunnlag

import no.nav.su.se.bakover.common.persistence.Session
import no.nav.su.se.bakover.common.persistence.TransactionalSession
import no.nav.su.se.bakover.database.skatt.SkattPostgresRepo
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlagSkatt
import no.nav.su.se.bakover.domain.grunnlag.SkattegrunnlagMedId
import java.util.UUID

internal class EksternGrunnlagPostgresRepo(
    private val skattRepo: SkattPostgresRepo,
) {

    fun lagre(sakId: UUID, eksternegrunnlag: EksterneGrunnlag, session: TransactionalSession) {
        skattRepo.lagre(sakId, eksternegrunnlag.skatt, session)
    }

    fun hentSkattegrunnlag(søkersId: UUID?, epsId: UUID?, session: Session): EksterneGrunnlagSkatt {
        val søkers = søkersId?.let { skattRepo.hent(it, session) }
        val eps = epsId?.let { skattRepo.hent(it, session) }

        return when (søkersId) {
            null -> EksterneGrunnlagSkatt.IkkeHentet
            else -> EksterneGrunnlagSkatt.Hentet(
                søkers = SkattegrunnlagMedId(id = søkersId, skattegrunnlag = søkers!!),
                eps = if (eps != null) SkattegrunnlagMedId(id = epsId, skattegrunnlag = eps) else null,
            )
        }
    }
}
