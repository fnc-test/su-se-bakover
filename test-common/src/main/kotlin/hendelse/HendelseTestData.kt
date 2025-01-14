package no.nav.su.se.bakover.test.hendelse

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.JMSHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.SakOpprettetHendelse
import no.nav.su.se.bakover.test.correlationId
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.saksbehandler
import java.util.UUID

fun sakOpprettetHendelse(
    hendelseId: HendelseId = HendelseId.generer(),
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    opprettetAv: NavIdentBruker = saksbehandler,
    hendelsestidspunkt: Tidspunkt = fixedTidspunkt,
    meta: DefaultHendelseMetadata = defaultHendelseMetadata(),
) = SakOpprettetHendelse.fraPersistert(
    hendelseId = hendelseId,
    sakId = sakId,
    fnr = fnr,
    opprettetAv = opprettetAv,
    hendelsestidspunkt = hendelsestidspunkt,
    meta = meta,
    entitetId = sakId,
    versjon = 1,
)

fun defaultHendelseMetadata(
    correlationId: CorrelationId? = correlationId(),
    ident: NavIdentBruker? = saksbehandler,
    brukerroller: List<Brukerrolle> = listOf(Brukerrolle.Saksbehandler, Brukerrolle.Attestant),
) = DefaultHendelseMetadata(
    correlationId = correlationId,
    ident = ident,
    brukerroller = brukerroller,
)

fun jmsHendelseMetadata(
    jmsCorrelationId: String = "jmsCorrelationId",
    jmsDeliveryMode: Int = 1,
    jmsDeliveryTime: Long = 1,
    jmsDestination: String = "jmsDestination",
    jmsExpiration: Long = 1,
    jmsMessageId: String = "jmsMessageId",
    jmsPriority: Int = 1,
    jmsRedelivered: Boolean = false,
    jmsReplyTo: String = "jmsReplyTo",
    jmsTimestamp: Long = 1,
    jmsType: String = "jmsType",
    correlationId: CorrelationId = CorrelationId("correlationId"),
): JMSHendelseMetadata = JMSHendelseMetadata(
    jmsCorrelationId = jmsCorrelationId,
    jmsDeliveryMode = jmsDeliveryMode,
    jmsDeliveryTime = jmsDeliveryTime,
    jmsDestination = jmsDestination,
    jmsExpiration = jmsExpiration,
    jmsMessageId = jmsMessageId,
    jmsPriority = jmsPriority,
    jmsRedelivered = jmsRedelivered,
    jmsReplyTo = jmsReplyTo,
    jmsTimestamp = jmsTimestamp,
    jmsType = jmsType,
    correlationId = correlationId,
)
