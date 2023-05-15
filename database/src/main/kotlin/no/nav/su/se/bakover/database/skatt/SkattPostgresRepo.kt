package no.nav.su.se.bakover.database.skatt

import kotliquery.Row
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.YearRange
import no.nav.su.se.bakover.common.persistence.Session
import no.nav.su.se.bakover.common.persistence.hent
import no.nav.su.se.bakover.common.persistence.insert
import no.nav.su.se.bakover.common.persistence.oppdatering
import no.nav.su.se.bakover.common.persistence.tidspunkt
import no.nav.su.se.bakover.database.common.YearRangeJson.Companion.toStringifiedYearRangeJson
import no.nav.su.se.bakover.database.skatt.SkattegrunnlagDbJson.Companion.toDbJson
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import java.util.UUID

internal object SkattPostgresRepo {
    fun hent(id: UUID, session: Session): Skattegrunnlag? =
        "SELECT * FROM skatt WHERE id = :id".hent(mapOf("id" to id), session) {
            it.toSkattegrunnlag()
        }

    fun slettSkattegrunnlag(id: UUID, session: Session) {
        "delete from skatt where id=:id".oppdatering(mapOf("id" to id), session)
    }

    fun lagreSkattegrunnlag(
        id: UUID,
        sakId: UUID,
        fnr: Fnr,
        erEps: Boolean,
        opprettet: Tidspunkt,
        data: Skattegrunnlag?,
        saksbehandler: NavIdentBruker.Saksbehandler,
        årSpurtFor: YearRange,
        session: Session,
    ) {
        lagreSkattekall(
            id = id,
            sakId = sakId,
            fnr = fnr,
            erEps = erEps,
            opprettet = opprettet,
            data = data,
            session = session,
            saksbehandler = saksbehandler,
            årSpurtFor = årSpurtFor,
        )
    }

    private fun lagreSkattekall(
        id: UUID,
        sakId: UUID,
        fnr: Fnr,
        erEps: Boolean,
        opprettet: Tidspunkt,
        data: Skattegrunnlag?,
        saksbehandler: NavIdentBruker.Saksbehandler,
        årSpurtFor: YearRange,
        session: Session,
    ) {
        """
                    insert into
                        skatt (id, sakId, fnr, erEps, opprettet, saksbehandler, årSpurtFor, data)
                    values
                        (:id, :sakId, :fnr, :erEps, :opprettet, :saksbehandler, to_json(:aarSpurtFor::jsonb), to_json(:data::jsonb))
        """.trimIndent().insert(
            mapOf(
                "id" to id,
                "sakId" to sakId,
                "fnr" to fnr.toString(),
                "erEps" to erEps,
                "opprettet" to opprettet,
                "saksbehandler" to saksbehandler,
                "aarSpurtFor" to årSpurtFor.toStringifiedYearRangeJson(),
                "data" to data?.toDbJson(),
            ),
            session = session,
        )
    }
}

fun Row.toSkattegrunnlag(): Skattegrunnlag {
    return SkattegrunnlagDbJson.toSkattegrunnlag(
        årsgrunnlagJson = string("data"),
        fnr = string("fnr"),
        hentetTidspunkt = tidspunkt("opprettet"),
        saksbehandler = string("saksbehandler"),
        årSpurtFor = string("årSpurtFor"),
    )
}