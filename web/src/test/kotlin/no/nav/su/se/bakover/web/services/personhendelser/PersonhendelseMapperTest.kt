package no.nav.su.se.bakover.web.services.personhendelser

import arrow.core.NonEmptyList
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.doedsfall.Doedsfall
import no.nav.person.pdl.leesah.sivilstand.Sivilstand
import no.nav.person.pdl.leesah.utflytting.UtflyttingFraNorge
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.hendelse.Personhendelse
import no.nav.su.se.bakover.domain.person.SivilstandTyper
import no.nav.su.se.bakover.service.FnrGenerator
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import no.nav.person.pdl.leesah.Personhendelse as EksternPersonhendelse

internal class PersonhendelseMapperTest {
    private val TOPIC = "topic"
    private val PARTITION = 0
    private val OFFSET = 0L
    private val fixedClock: Clock = Clock.fixed(1.januar(2021).startOfDay().instant, ZoneOffset.UTC)

    private val aktørId = "1234567890000"
    private val fnr = FnrGenerator.random().toString()
    private val opprettet = Instant.now(fixedClock)
    private val tidspunkt = LocalDate.now(fixedClock)

    @Test
    fun `mapper fra ekstern dødsfalltype til intern`() {
        val personhendelse = EksternPersonhendelse(
            "hendelseId",
            listOf(fnr, aktørId),
            "FREG",
            opprettet,
            "DOEDSFALL_V1",
            Endringstype.OPPRETTET,
            null,
            Doedsfall(tidspunkt),
            null,
            null,
        )
        val message = ConsumerRecord(TOPIC, PARTITION, OFFSET, aktørId, personhendelse)
        val actual = HendelseMapper.map(message).getOrElse { throw RuntimeException("Feil skjedde i test") }

        actual shouldBe Personhendelse.Ny(
            gjeldendeAktørId = AktørId(aktørId),
            endringstype = Personhendelse.Endringstype.OPPRETTET,
            hendelse = Personhendelse.Hendelse.Dødsfall(tidspunkt),
            personidenter = NonEmptyList.fromListUnsafe(personhendelse.getPersonidenter()),
            metadata = Personhendelse.Metadata(
                hendelseId = "hendelseId",
                tidligereHendelseId = null,
                offset = OFFSET,
                partisjon = PARTITION,
                master = "FREG",
                key = aktørId,
            ),
        )
    }

    @Test
    fun `mapper fra ekstern utflyttingstype til intern`() {
        val personhendelse = EksternPersonhendelse(
            "hendelseId",
            listOf(fnr, aktørId),
            "FREG",
            opprettet,
            "UTFLYTTING_FRA_NORGE",
            Endringstype.OPPRETTET,
            null,
            null,
            null,
            UtflyttingFraNorge("Sverige", "Stockholm", tidspunkt),
        )
        val message = ConsumerRecord(TOPIC, PARTITION, OFFSET, aktørId, personhendelse)
        val actual = HendelseMapper.map(message).getOrElse { throw RuntimeException("Feil skjedde i test") }

        actual shouldBe Personhendelse.Ny(
            gjeldendeAktørId = AktørId(aktørId),
            endringstype = Personhendelse.Endringstype.OPPRETTET,
            hendelse = Personhendelse.Hendelse.UtflyttingFraNorge(tidspunkt),
            personidenter = NonEmptyList.fromListUnsafe(personhendelse.getPersonidenter()),
            metadata = Personhendelse.Metadata(
                hendelseId = "hendelseId",
                tidligereHendelseId = null,
                offset = OFFSET,
                partisjon = PARTITION,
                master = "FREG",
                key = aktørId,
            ),
        )
    }

    @Test
    fun `mapper fra ekstern sivilstand til intern`() {
        val personhendelse = EksternPersonhendelse(
            "hendelseId",
            listOf(fnr, aktørId),
            "FREG",
            opprettet,
            "SIVILSTAND_V1",
            Endringstype.OPPRETTET,
            null,
            null,
            Sivilstand("UGIFT", null, null, null),
            null,
        )
        val message = ConsumerRecord(TOPIC, PARTITION, OFFSET, aktørId, personhendelse)
        val actual = HendelseMapper.map(message).getOrElse { throw RuntimeException("Feil skjedde i test") }

        actual shouldBe Personhendelse.Ny(
            gjeldendeAktørId = AktørId(aktørId),
            endringstype = Personhendelse.Endringstype.OPPRETTET,
            hendelse = Personhendelse.Hendelse.Sivilstand(SivilstandTyper.UGIFT, null, null, null),
            personidenter = NonEmptyList.fromListUnsafe(personhendelse.getPersonidenter()),
            metadata = Personhendelse.Metadata(
                hendelseId = "hendelseId",
                tidligereHendelseId = null,
                offset = OFFSET,
                partisjon = PARTITION,
                master = "FREG",
                key = aktørId,
            ),
        )
    }

    @Test
    fun `støtter prepend i key`() {
        val personhendelse = EksternPersonhendelse(
            "hendelseId",
            listOf(fnr, aktørId),
            "FREG",
            opprettet,
            "UTFLYTTING_FRA_NORGE",
            Endringstype.OPPRETTET,
            null,
            null,
            null,
            UtflyttingFraNorge("Sverige", "Stockholm", tidspunkt),
        )
        val message = ConsumerRecord(TOPIC, PARTITION, OFFSET, "\u0000$aktørId", personhendelse)
        val actual = HendelseMapper.map(message)

        actual shouldBe Personhendelse.Ny(
            gjeldendeAktørId = AktørId(aktørId),
            endringstype = Personhendelse.Endringstype.OPPRETTET,
            hendelse = Personhendelse.Hendelse.UtflyttingFraNorge(tidspunkt),
            personidenter = NonEmptyList.fromListUnsafe(personhendelse.getPersonidenter()),
            metadata = Personhendelse.Metadata(
                hendelseId = "hendelseId",
                tidligereHendelseId = null,
                offset = OFFSET,
                partisjon = PARTITION,
                master = "FREG",
                key = "\u0000$aktørId",
            ),
        ).right()
    }

    @Test
    fun `aktørId som ikke finnes i personidenter gir feil`() {
        val personhendelse = EksternPersonhendelse(
            "hendelseId",
            listOf(fnr),
            "FREG",
            opprettet,
            "UTFLYTTING_FRA_NORGE",
            Endringstype.OPPRETTET,
            null,
            null,
            null,
            UtflyttingFraNorge("Sverige", "Stockholm", tidspunkt),
        )
        val message = ConsumerRecord(TOPIC, PARTITION, OFFSET, aktørId, personhendelse)
        val actual = HendelseMapper.map(message)

        actual shouldBe KunneIkkeMappePersonhendelse.KunneIkkeHenteAktørId("hendelseId", "UTFLYTTING_FRA_NORGE").left()
    }

    @Test
    fun `skipper hendelser vi ikke er intressert i`() {
        val personhendelse = EksternPersonhendelse(
            "hendelseId",
            listOf(fnr, aktørId),
            "FREG",
            opprettet,
            "FOEDSEL_V1",
            Endringstype.OPPRETTET,
            null,
            null,
            null,
            UtflyttingFraNorge("Sverige", "Stockholm", tidspunkt),
        )
        val message = ConsumerRecord(TOPIC, PARTITION, OFFSET, aktørId, personhendelse)
        val actual = HendelseMapper.map(message)

        actual shouldBe KunneIkkeMappePersonhendelse.IkkeAktuellOpplysningstype("hendelseId", "FOEDSEL_V1").left()
    }
}