package no.nav.su.se.bakover.database.klage

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import kotliquery.Row
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.database.PostgresSessionFactory
import no.nav.su.se.bakover.database.PostgresTransactionContext.Companion.withTransaction
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.attestering.toAttesteringshistorikk
import no.nav.su.se.bakover.database.attestering.toDatabaseJson
import no.nav.su.se.bakover.database.booleanOrNull
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo.Svarord.Companion.tilDatabaseType
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo.Tilstand.Companion.databasetype
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo.VedtaksvurderingJson.Companion.toJson
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo.VedtaksvurderingJson.Omgjør.Omgjøringsutfall.Companion.toDatabasetype
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo.VedtaksvurderingJson.Omgjør.Omgjøringsårsak.Companion.toDatabasetype
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo.VedtaksvurderingJson.Oppretthold.Hjemmel.Companion.toDatabasetype
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.database.uuidOrNull
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.Hjemler
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.time.LocalDate
import java.util.UUID

internal class KlagePostgresRepo(private val sessionFactory: PostgresSessionFactory) : KlageRepo {
    override fun lagre(klage: Klage, transactionContext: TransactionContext) {
        transactionContext.withTransaction { transaction ->
            when (klage) {
                is OpprettetKlage -> lagreOpprettetKlage(klage, transaction)
                is VilkårsvurdertKlage -> lagreVilkårsvurdertKlage(klage, transaction)
                is VurdertKlage -> lagreVurdertKlage(klage, transaction)
                is KlageTilAttestering -> lagreTilAttestering(klage, transaction)
                is OversendtKlage -> lagreOversendtKlage(klage, transaction)
            }
        }
    }

    private fun lagreOpprettetKlage(klage: OpprettetKlage, session: Session) {
        """
            insert into klage(id,  sakid,  opprettet,  journalpostid,  oppgaveid,  saksbehandler,  datoKlageMottatt,  type)
                      values(:id, :sakid, :opprettet, :journalpostid, :oppgaveid, :saksbehandler, :datoKlageMottatt, :type)
        """.trimIndent()
            .insert(
                params = mapOf(
                    "id" to klage.id,
                    "sakid" to klage.sakId,
                    "opprettet" to klage.opprettet,
                    "journalpostid" to klage.journalpostId,
                    "oppgaveid" to klage.oppgaveId,
                    "saksbehandler" to klage.saksbehandler,
                    "datoKlageMottatt" to klage.datoKlageMottatt,
                    "type" to klage.databasetype(),
                ),
                session = session,
            )
    }

    private fun lagreVilkårsvurdertKlage(klage: VilkårsvurdertKlage, session: Session) {
        """
            update klage set
                oppgaveid=:oppgaveid,
                saksbehandler=:saksbehandler,
                type=:type,
                attestering = to_jsonb(:attestering::jsonb),
                vedtakId=:vedtakId,
                innenforFristen=:innenforFristen,
                klagesDetPåKonkreteElementerIVedtaket=:klagesDetPaaKonkreteElementerIVedtaket,
                erUnderskrevet=:erUnderskrevet,
                begrunnelse=:begrunnelse
            where id=:id
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "id" to klage.id,
                    "oppgaveid" to klage.oppgaveId,
                    "saksbehandler" to klage.saksbehandler,
                    "type" to klage.databasetype(),
                    "attestering" to klage.attesteringer.toDatabaseJson(),
                    "vedtakId" to klage.vilkårsvurderinger.vedtakId,
                    "innenforFristen" to klage.vilkårsvurderinger.innenforFristen?.tilDatabaseType(),
                    "klagesDetPaaKonkreteElementerIVedtaket" to klage.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
                    "erUnderskrevet" to klage.vilkårsvurderinger.erUnderskrevet?.tilDatabaseType(),
                    "begrunnelse" to klage.vilkårsvurderinger.begrunnelse,
                ),
                session,
            )
    }

    private fun lagreVurdertKlage(klage: VurdertKlage, session: Session) {
        """
            update klage set
                oppgaveid=:oppgaveid,
                saksbehandler=:saksbehandler,
                type=:type,
                attestering=to_jsonb(:attestering::jsonb),
                vedtakId=:vedtakId,
                innenforFristen=:innenforFristen,
                klagesDetPåKonkreteElementerIVedtaket=:klagesDetPaaKonkreteElementerIVedtaket,
                erUnderskrevet=:erUnderskrevet,
                begrunnelse=:begrunnelse,
                fritekstTilBrev=:fritekstTilBrev,
                vedtaksvurdering=to_jsonb(:vedtaksvurdering::jsonb)
            where id=:id
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "id" to klage.id,
                    "oppgaveid" to klage.oppgaveId,
                    "saksbehandler" to klage.saksbehandler,
                    "type" to klage.databasetype(),
                    "vedtakId" to klage.vilkårsvurderinger.vedtakId,
                    "innenforFristen" to klage.vilkårsvurderinger.innenforFristen?.tilDatabaseType(),
                    "klagesDetPaaKonkreteElementerIVedtaket" to klage.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
                    "erUnderskrevet" to klage.vilkårsvurderinger.erUnderskrevet?.tilDatabaseType(),
                    "begrunnelse" to klage.vilkårsvurderinger.begrunnelse,
                    "fritekstTilBrev" to klage.vurderinger.fritekstTilBrev,
                    "vedtaksvurdering" to klage.vurderinger.vedtaksvurdering?.toJson(),
                    "attestering" to klage.attesteringer.toDatabaseJson(),
                ),
                session,
            )
    }

    private fun lagreTilAttestering(klage: KlageTilAttestering, session: Session) {
        """
            update 
                klage 
            set
                oppgaveid=:oppgaveid,
                type=:type,
                saksbehandler=:saksbehandler,
                attestering=to_jsonb(:attestering::jsonb)
            where id=:id
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "id" to klage.id,
                    "oppgaveid" to klage.oppgaveId,
                    "type" to klage.databasetype(),
                    "saksbehandler" to klage.saksbehandler,
                    "attestering" to klage.attesteringer.toDatabaseJson(),
                ),
                session,
            )
    }

    private fun lagreOversendtKlage(klage: OversendtKlage, session: Session) {
        """
            update
                klage
            set
                oppgaveid=:oppgaveid,
                type=:type,
                attestering=to_jsonb(:attestering::jsonb)
            where id=:id
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "id" to klage.id,
                    "oppgaveid" to klage.oppgaveId,
                    "type" to klage.databasetype(),
                    "attestering" to klage.attesteringer.toDatabaseJson(),
                ),
                session,
            )
    }

    override fun hentKlage(klageId: UUID): Klage? {
        return sessionFactory.withSession { session ->
            "select k.*, s.fnr, s.saksnummer  from klage k inner join sak s on s.id = k.sakId where k.id=:id".trimIndent()
                .hent(
                    params = mapOf("id" to klageId),
                    session = session,
                ) { rowToKlage(it) }
        }
    }

    override fun hentKlager(sakid: UUID, sessionContext: SessionContext): List<Klage> {
        return sessionContext.withSession { session ->
            """
                    select k.*, s.fnr, s.saksnummer  from klage k inner join sak s on s.id = k.sakId where k.sakid=:sakid
            """.trimIndent().hentListe(
                mapOf(
                    "sakid" to sakid,
                ),
                session,
            ) { rowToKlage(it) }
        }
    }

    override fun hentKnyttetVedtaksdato(klageId: UUID): LocalDate? {
        return sessionFactory.withSession {
            """
                select v.opprettet from klage k left join vedtak v on k.vedtakid = v.id
                    where k.id = :id
            """.trimIndent()
                .hent(mapOf("id" to klageId), it) { row ->
                    row.localDate("opprettet")
                }
        }
    }

    override fun defaultSessionContext(): SessionContext {
        return sessionFactory.newSessionContext()
    }

    override fun defaultTransactionContext(): TransactionContext {
        return sessionFactory.newTransactionContext()
    }

    private fun rowToKlage(row: Row): Klage {

        val id: UUID = row.uuid("id")
        val opprettet: Tidspunkt = row.tidspunkt("opprettet")
        val sakId: UUID = row.uuid("sakid")
        val saksnummer = Saksnummer(row.long("saksnummer"))
        val fnr = Fnr(row.string("fnr"))
        val journalpostId = JournalpostId(row.string("journalpostid"))
        val oppgaveId = OppgaveId(row.string("oppgaveId"))
        val datoKlageMottatt = row.localDate("datoKlageMottatt")
        val saksbehandler: NavIdentBruker.Saksbehandler = NavIdentBruker.Saksbehandler(row.string("saksbehandler"))

        val attesteringer = row.string("attestering").toAttesteringshistorikk()

        val vilkårsvurderingerTilKlage = VilkårsvurderingerTilKlage.create(
            vedtakId = row.uuidOrNull("vedtakId"),
            innenforFristen = row.stringOrNull("innenforFristen")?.let {
                VilkårsvurderingerTilKlage.Svarord.valueOf(it)
            },
            klagesDetPåKonkreteElementerIVedtaket = row.booleanOrNull("klagesDetPåKonkreteElementerIVedtaket"),
            erUnderskrevet = row.stringOrNull("erUnderskrevet")?.let {
                VilkårsvurderingerTilKlage.Svarord.valueOf(it)
            },
            begrunnelse = row.stringOrNull("begrunnelse"),
        )

        val fritekstTilBrev = row.stringOrNull("fritekstTilBrev")
        val vedtaksvurdering = row.stringOrNull("vedtaksvurdering")?.let {
            deserialize<VedtaksvurderingJson>(it).toDomain()
        }
        val vurderinger = if (fritekstTilBrev == null && vedtaksvurdering == null) null else VurderingerTilKlage.create(
            fritekstTilBrev = fritekstTilBrev,
            vedtaksvurdering = vedtaksvurdering,
        )

        return when (Tilstand.fromString(row.string("type"))) {
            Tilstand.OPPRETTET -> OpprettetKlage.create(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                journalpostId = journalpostId,
                oppgaveId = oppgaveId,
                saksbehandler = saksbehandler,
                datoKlageMottatt = datoKlageMottatt,
            )
            Tilstand.VILKÅRSVURDERT_PÅBEGYNT -> {
                VilkårsvurdertKlage.Påbegynt.create(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    fnr = fnr,
                    journalpostId = journalpostId,
                    oppgaveId = oppgaveId,
                    saksbehandler = saksbehandler,
                    vilkårsvurderinger = vilkårsvurderingerTilKlage as VilkårsvurderingerTilKlage.Påbegynt,
                    vurderinger = vurderinger,
                    attesteringer = attesteringer,
                    datoKlageMottatt = datoKlageMottatt,
                )
            }
            Tilstand.VILKÅRSVURDERT_UTFYLT -> VilkårsvurdertKlage.Utfylt.create(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                journalpostId = journalpostId,
                oppgaveId = oppgaveId,
                saksbehandler = saksbehandler,
                vilkårsvurderinger = vilkårsvurderingerTilKlage as VilkårsvurderingerTilKlage.Utfylt,
                vurderinger = vurderinger,
                attesteringer = attesteringer,
                datoKlageMottatt = datoKlageMottatt,
            )
            Tilstand.VILKÅRSVURDERT_BEKREFTET -> VilkårsvurdertKlage.Bekreftet.create(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                journalpostId = journalpostId,
                oppgaveId = oppgaveId,
                saksbehandler = saksbehandler,
                vilkårsvurderinger = vilkårsvurderingerTilKlage as VilkårsvurderingerTilKlage.Utfylt,
                vurderinger = vurderinger,
                attesteringer = attesteringer,
                datoKlageMottatt = datoKlageMottatt,
            )
            Tilstand.VURDERT_PÅBEGYNT -> VurdertKlage.Påbegynt.create(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                journalpostId = journalpostId,
                oppgaveId = oppgaveId,
                saksbehandler = saksbehandler,
                vilkårsvurderinger = vilkårsvurderingerTilKlage as VilkårsvurderingerTilKlage.Utfylt,
                vurderinger = if (vurderinger == null) VurderingerTilKlage.empty() else vurderinger as VurderingerTilKlage.Påbegynt,
                attesteringer = attesteringer,
                datoKlageMottatt = datoKlageMottatt,
            )
            Tilstand.VURDERT_UTFYLT -> VurdertKlage.Utfylt.create(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                journalpostId = journalpostId,
                oppgaveId = oppgaveId,
                saksbehandler = saksbehandler,
                vilkårsvurderinger = vilkårsvurderingerTilKlage as VilkårsvurderingerTilKlage.Utfylt,
                vurderinger = vurderinger as VurderingerTilKlage.Utfylt,
                attesteringer = attesteringer,
                datoKlageMottatt = datoKlageMottatt,
            )
            Tilstand.VURDERT_BEKREFTET -> VurdertKlage.Bekreftet.create(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                journalpostId = journalpostId,
                oppgaveId = oppgaveId,
                saksbehandler = saksbehandler,
                vilkårsvurderinger = vilkårsvurderingerTilKlage as VilkårsvurderingerTilKlage.Utfylt,
                vurderinger = vurderinger as VurderingerTilKlage.Utfylt,
                attesteringer = attesteringer,
                datoKlageMottatt = datoKlageMottatt,
            )
            Tilstand.TIL_ATTESTERING -> KlageTilAttestering.create(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                journalpostId = journalpostId,
                oppgaveId = oppgaveId,
                saksbehandler = saksbehandler,
                vilkårsvurderinger = vilkårsvurderingerTilKlage as VilkårsvurderingerTilKlage.Utfylt,
                vurderinger = vurderinger as VurderingerTilKlage.Utfylt,
                attesteringer = attesteringer,
                datoKlageMottatt = datoKlageMottatt,
            )
            Tilstand.OVERSENDT -> OversendtKlage.create(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                journalpostId = journalpostId,
                oppgaveId = oppgaveId,
                saksbehandler = saksbehandler,
                vilkårsvurderinger = vilkårsvurderingerTilKlage as VilkårsvurderingerTilKlage.Utfylt,
                vurderinger = vurderinger as VurderingerTilKlage.Utfylt,
                attesteringer = attesteringer,
                datoKlageMottatt = datoKlageMottatt,
            )
        }
    }

    private enum class Svarord {
        JA,
        NEI_MEN_SKAL_VURDERES,
        NEI;

        companion object {
            fun VilkårsvurderingerTilKlage.Svarord.tilDatabaseType(): String {
                return when (this) {
                    VilkårsvurderingerTilKlage.Svarord.JA -> JA
                    VilkårsvurderingerTilKlage.Svarord.NEI_MEN_SKAL_VURDERES -> NEI_MEN_SKAL_VURDERES
                    VilkårsvurderingerTilKlage.Svarord.NEI -> NEI
                }.toString()
            }
        }
    }

    private enum class Tilstand(val verdi: String) {
        OPPRETTET("opprettet"),
        VILKÅRSVURDERT_PÅBEGYNT("vilkårsvurdert_påbegynt"),
        VILKÅRSVURDERT_UTFYLT("vilkårsvurdert_utfylt"),
        VILKÅRSVURDERT_BEKREFTET("vilkårsvurdert_bekreftet"),
        VURDERT_PÅBEGYNT("vurdert_påbegynt"),
        VURDERT_UTFYLT("vurdert_utfylt"),
        VURDERT_BEKREFTET("vurdert_bekreftet"),
        TIL_ATTESTERING("til_attestering"),
        OVERSENDT("oversendt");

        companion object {
            fun Klage.databasetype(): String {
                return when (this) {
                    is OpprettetKlage -> OPPRETTET
                    is VilkårsvurdertKlage.Påbegynt -> VILKÅRSVURDERT_PÅBEGYNT
                    is VilkårsvurdertKlage.Utfylt -> VILKÅRSVURDERT_UTFYLT
                    is VilkårsvurdertKlage.Bekreftet -> VILKÅRSVURDERT_BEKREFTET
                    is VurdertKlage.Påbegynt -> VURDERT_PÅBEGYNT
                    is VurdertKlage.Utfylt -> VURDERT_UTFYLT
                    is VurdertKlage.Bekreftet -> VURDERT_BEKREFTET
                    is KlageTilAttestering -> TIL_ATTESTERING
                    is OversendtKlage -> OVERSENDT
                }.toString()
            }

            fun fromString(value: String): Tilstand {
                return values().find { it.verdi == value }
                    ?: throw IllegalStateException("Ukjent tilstand i klage-tabellen: $value")
            }
        }

        override fun toString() = verdi
    }

    /**
     * Databasenversjonen av [VurderingerTilKlage]
     *
     * Merk at man kan ha valgt utfall, uten å ha fylt inn innholdet. Selve kravene til utfylling styres av domenet vha. feltet 'type' (tilstand) i tabellen klage.
     */
    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
    )
    @JsonSubTypes(
        JsonSubTypes.Type(
            value = VedtaksvurderingJson.Omgjør::class,
            name = "Omgjør",
        ),
        JsonSubTypes.Type(
            value = VedtaksvurderingJson.Oppretthold::class,
            name = "Oppretthold",
        ),
    )
    private sealed class VedtaksvurderingJson {
        abstract fun toDomain(): VurderingerTilKlage.Vedtaksvurdering
        data class Omgjør(val årsak: String?, val utfall: String?) : VedtaksvurderingJson() {

            override fun toDomain(): VurderingerTilKlage.Vedtaksvurdering {
                return VurderingerTilKlage.Vedtaksvurdering.createOmgjør(
                    årsak = årsak?.let { Omgjøringsårsak.fromString(it).toDomain() },
                    utfall = utfall?.let { Omgjøringsutfall.fromString(it).toDomain() },
                )
            }

            enum class Omgjøringsårsak(val verdi: String) {
                FEIL_LOVANVENDELSE("feil_lovanvendelse"),
                ULIK_SKJØNNSVURDERING("ulik_skjønnsvurdering"),
                SAKSBEHANDLINGSFEIL("saksbehandlingsfeil"),
                NYTT_FAKTUM("nytt_faktum");

                fun toDomain(): VurderingerTilKlage.Vedtaksvurdering.Årsak {
                    return when (this) {
                        FEIL_LOVANVENDELSE -> VurderingerTilKlage.Vedtaksvurdering.Årsak.FEIL_LOVANVENDELSE
                        ULIK_SKJØNNSVURDERING -> VurderingerTilKlage.Vedtaksvurdering.Årsak.ULIK_SKJØNNSVURDERING
                        SAKSBEHANDLINGSFEIL -> VurderingerTilKlage.Vedtaksvurdering.Årsak.SAKSBEHANDLINGSFEIL
                        NYTT_FAKTUM -> VurderingerTilKlage.Vedtaksvurdering.Årsak.NYTT_FAKTUM
                    }
                }

                companion object {
                    fun VurderingerTilKlage.Vedtaksvurdering.Årsak.toDatabasetype(): String {
                        return when (this) {
                            VurderingerTilKlage.Vedtaksvurdering.Årsak.FEIL_LOVANVENDELSE -> FEIL_LOVANVENDELSE
                            VurderingerTilKlage.Vedtaksvurdering.Årsak.ULIK_SKJØNNSVURDERING -> ULIK_SKJØNNSVURDERING
                            VurderingerTilKlage.Vedtaksvurdering.Årsak.SAKSBEHANDLINGSFEIL -> SAKSBEHANDLINGSFEIL
                            VurderingerTilKlage.Vedtaksvurdering.Årsak.NYTT_FAKTUM -> NYTT_FAKTUM
                        }.toString()
                    }

                    fun fromString(value: String): Omgjøringsårsak {
                        return values().find { it.verdi == value }
                            ?: throw IllegalStateException("Ukjent omgjøringsårsak i klage-tabellen: $value")
                    }
                }

                override fun toString() = verdi
            }

            enum class Omgjøringsutfall(val verdi: String) {
                TIL_GUNST("til_gunst"),
                TIL_UGUNST("til_ugunst");

                fun toDomain(): VurderingerTilKlage.Vedtaksvurdering.Utfall {
                    return when (this) {
                        TIL_GUNST -> VurderingerTilKlage.Vedtaksvurdering.Utfall.TIL_GUNST
                        TIL_UGUNST -> VurderingerTilKlage.Vedtaksvurdering.Utfall.TIL_UGUNST
                    }
                }

                companion object {
                    fun VurderingerTilKlage.Vedtaksvurdering.Utfall.toDatabasetype(): String {
                        return when (this) {
                            VurderingerTilKlage.Vedtaksvurdering.Utfall.TIL_GUNST -> TIL_GUNST
                            VurderingerTilKlage.Vedtaksvurdering.Utfall.TIL_UGUNST -> TIL_UGUNST
                        }.toString()
                    }

                    fun fromString(value: String): Omgjøringsutfall {
                        return values().find { it.verdi == value }
                            ?: throw IllegalStateException("Ukjent omgjøringsutfall i klage-tabellen: $value")
                    }
                }

                override fun toString() = verdi
            }
        }

        data class Oppretthold(val hjemler: List<String>) : VedtaksvurderingJson() {

            enum class Hjemmel(val verdi: String) {
                SU_PARAGRAF_3("su_paragraf_3"),
                SU_PARAGRAF_4("su_paragraf_4"),
                SU_PARAGRAF_5("su_paragraf_5"),
                SU_PARAGRAF_6("su_paragraf_6"),
                SU_PARAGRAF_8("su_paragraf_8"),
                SU_PARAGRAF_9("su_paragraf_9"),
                SU_PARAGRAF_10("su_paragraf_10"),
                SU_PARAGRAF_12("su_paragraf_12"),
                SU_PARAGRAF_13("su_paragraf_13"),
                SU_PARAGRAF_18("su_paragraf_18");

                fun toDomain(): no.nav.su.se.bakover.domain.klage.Hjemmel {
                    return when (this) {
                        SU_PARAGRAF_3 -> no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_3
                        SU_PARAGRAF_4 -> no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_4
                        SU_PARAGRAF_5 -> no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_5
                        SU_PARAGRAF_6 -> no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_6
                        SU_PARAGRAF_8 -> no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_8
                        SU_PARAGRAF_9 -> no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_9
                        SU_PARAGRAF_10 -> no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_10
                        SU_PARAGRAF_12 -> no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_12
                        SU_PARAGRAF_13 -> no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_13
                        SU_PARAGRAF_18 -> no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_18
                    }
                }

                companion object {
                    fun Hjemler.toDatabasetype(): List<String> {
                        return this.map { hjemmel ->
                            when (hjemmel) {
                                no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_3 -> SU_PARAGRAF_3
                                no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_4 -> SU_PARAGRAF_4
                                no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_5 -> SU_PARAGRAF_5
                                no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_6 -> SU_PARAGRAF_6
                                no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_8 -> SU_PARAGRAF_8
                                no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_9 -> SU_PARAGRAF_9
                                no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_10 -> SU_PARAGRAF_10
                                no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_12 -> SU_PARAGRAF_12
                                no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_13 -> SU_PARAGRAF_13
                                no.nav.su.se.bakover.domain.klage.Hjemmel.SU_PARAGRAF_18 -> SU_PARAGRAF_18
                            }.toString()
                        }
                    }

                    fun fromString(value: String): Hjemmel {
                        return values().find { it.verdi == value }
                            ?: throw IllegalStateException("Ukjent omgjøringsutfall i klage-tabellen: $value")
                    }
                }

                override fun toString() = verdi
            }

            override fun toDomain(): VurderingerTilKlage.Vedtaksvurdering {
                return VurderingerTilKlage.Vedtaksvurdering.createOppretthold(
                    hjemler = hjemler.map { Hjemmel.fromString(it).toDomain() },
                ).orNull()!!
            }
        }

        companion object {
            fun VurderingerTilKlage.Vedtaksvurdering.toJson(): String {
                return when (this) {
                    is VurderingerTilKlage.Vedtaksvurdering.Påbegynt.Omgjør -> Omgjør(
                        årsak = årsak?.toDatabasetype(),
                        utfall = utfall?.toDatabasetype(),
                    )
                    is VurderingerTilKlage.Vedtaksvurdering.Påbegynt.Oppretthold -> Oppretthold(
                        hjemler = hjemler.toDatabasetype(),
                    )
                    is VurderingerTilKlage.Vedtaksvurdering.Utfylt.Omgjør -> Omgjør(
                        årsak = årsak.toDatabasetype(),
                        utfall = utfall.toDatabasetype(),
                    )
                    is VurderingerTilKlage.Vedtaksvurdering.Utfylt.Oppretthold -> Oppretthold(
                        hjemler = hjemler.toDatabasetype(),
                    )
                }.let {
                    serialize(it)
                }
            }
        }
    }
}