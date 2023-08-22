package no.nav.su.se.bakover.oppgave.infrastructure

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import no.nav.su.se.bakover.oppgave.infrastructure.OppgaveHendelseData.Companion.toStringifiedJson
import java.time.Clock
import java.util.UUID

const val OppgaveHendelsestype = "OPPGAVE"

val alleTyper = nonEmptyListOf(OppgaveHendelsestype)

class OppgaveHendelsePostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
    private val hendelseRepo: HendelsePostgresRepo,
    private val clock: Clock,
) : OppgaveHendelseRepo {

    override fun lagre(hendelse: OppgaveHendelse, sessionContext: SessionContext) {
        dbMetrics.timeQuery("lagreInstitusjonsoppholdHendelse") {
            hendelseRepo.persister(
                hendelse = hendelse,
                type = OppgaveHendelsestype,
                data = hendelse.toStringifiedJson(),
                sessionContext = sessionContext,
            )
        }
    }

    override fun hentForSak(sakId: UUID): List<OppgaveHendelse> {
        return dbMetrics.timeQuery("hentOppgaveHendelserForSak") {
            hendelseRepo.hentHendelserForSakIdOgTyper(sakId, alleTyper).map {
                it.toOppgaveHendelse()
            }
        }
    }

    private fun PersistertHendelse.toOppgaveHendelse(): OppgaveHendelse {
        val data = deserialize<OppgaveHendelseData>(this.data)
        return OppgaveHendelse(
            hendelseId = HendelseId.fromUUID(this.hendelseId),
            tidligereHendelseId = this.tidligereHendelseId?.let { HendelseId.fromUUID(it) },
            sakId = this.sakId!!,
            versjon = this.versjon,
            hendelsestidspunkt = this.hendelsestidspunkt,
            triggetAv = this.triggetAv?.let { HendelseId.fromUUID(it) },
            oppgaveId = OppgaveId(data.oppgaveId),
        )
    }
}
