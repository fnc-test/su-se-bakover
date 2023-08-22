package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import java.time.Clock
import java.util.UUID

/**
 * https://github.com/navikt/institusjon/blob/main/apps/institusjon-opphold-hendelser/src/main/java/no/nav/opphold/hendelser/producer/domain/KafkaOppholdHendelse.java
 */
data class EksternInstitusjonsoppholdHendelse(
    val hendelseId: Long,
    val oppholdId: OppholdId,
    val norskident: Fnr,
    val type: InstitusjonsoppholdType,
    val kilde: InstitusjonsoppholdKilde,
) {
    fun nyHendelsePåSak(
        sakId: UUID,
        versjon: Hendelsesversjon,
        clock: Clock,
    ): InstitusjonsoppholdHendelse = InstitusjonsoppholdHendelse(
        hendelseId = HendelseId.generer(),
        sakId = sakId,
        hendelsestidspunkt = Tidspunkt.now(clock),
        eksterneHendelse = this,
        versjon = versjon,
    )

    fun nyHendelsePåSakLenketTilEksisterendeHendelse(
        tidligereHendelse: InstitusjonsoppholdHendelse,
        versjon: Hendelsesversjon,
        clock: Clock,
    ): InstitusjonsoppholdHendelse = InstitusjonsoppholdHendelse(
        hendelseId = HendelseId.generer(),
        sakId = tidligereHendelse.sakId,
        versjon = versjon,
        hendelsestidspunkt = Tidspunkt.now(clock),
        tidligereHendelseId = tidligereHendelse.hendelseId,
        eksterneHendelse = this,
    )
}

data class OppholdId(val value: Long)

data class InstitusjonsoppholdHendelse(
    override val hendelseId: HendelseId,
    override val sakId: UUID,
    override val versjon: Hendelsesversjon,
    override val hendelsestidspunkt: Tidspunkt,
    override val tidligereHendelseId: HendelseId? = null,
    val eksterneHendelse: EksternInstitusjonsoppholdHendelse,
) : Sakshendelse {
    override val meta: HendelseMetadata = HendelseMetadata.tom()
    override val entitetId: UUID = sakId
    override val triggetAv: HendelseId? = null

    override fun compareTo(other: Sakshendelse): Int {
        require(this.entitetId == other.entitetId && this.sakId == other.sakId) { "EntitetIdene eller sakIdene var ikke lik" }
        return this.versjon.compareTo(other.versjon)
    }

    fun nyOppgaveHendelse(oppgaveId: OppgaveId, tidligereHendelse: OppgaveHendelse?, versjon: Hendelsesversjon, clock: Clock): OppgaveHendelse =
        OppgaveHendelse(
            hendelseId = HendelseId.generer(),
            tidligereHendelseId = tidligereHendelse?.tidligereHendelseId,
            sakId = this.sakId,
            versjon = versjon,
            hendelsestidspunkt = Tidspunkt.now(clock),
            triggetAv = this.hendelseId,
            oppgaveId = oppgaveId,
        )
}

fun List<InstitusjonsoppholdHendelse>.hentSisteHendelse(): InstitusjonsoppholdHendelse {
    return InstitusjonsoppholdHendelserPåSak(this.toNonEmptyList()).sisteHendelse()
}
