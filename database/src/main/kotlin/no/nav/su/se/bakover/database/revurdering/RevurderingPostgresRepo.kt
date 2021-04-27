package no.nav.su.se.bakover.database.revurdering

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.beregning.PersistertBeregning
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.database.vedtak.VedtakPosgresRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeslutningEtterForhåndsvarsling
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import java.util.UUID
import javax.sql.DataSource

interface RevurderingRepo {
    fun hent(id: UUID): Revurdering?
    fun hent(id: UUID, session: Session): Revurdering?
    fun hentEventuellTidligereAttestering(id: UUID): Attestering?
    fun lagre(revurdering: Revurdering)
    fun oppdaterForhåndsvarsel(id: UUID, forhåndsvarsel: Forhåndsvarsel)
}

enum class RevurderingsType {
    OPPRETTET,
    BEREGNET_INNVILGET,
    BEREGNET_OPPHØRT,
    BEREGNET_INGEN_ENDRING,
    SIMULERT_INNVILGET,
    SIMULERT_OPPHØRT,
    TIL_ATTESTERING_INNVILGET,
    TIL_ATTESTERING_OPPHØRT,
    TIL_ATTESTERING_INGEN_ENDRING,
    IVERKSATT_INNVILGET,
    IVERKSATT_OPPHØRT,
    IVERKSATT_INGEN_ENDRING,
    UNDERKJENT_INNVILGET,
    UNDERKJENT_OPPHØRT,
    UNDERKJENT_INGEN_ENDRING
}

internal class RevurderingPostgresRepo(
    private val dataSource: DataSource,
    internal val søknadsbehandlingRepo: SøknadsbehandlingRepo,
) : RevurderingRepo {
    private val vedtakRepo: VedtakRepo = VedtakPosgresRepo(dataSource, søknadsbehandlingRepo, this)

    override fun hent(id: UUID): Revurdering? =
        dataSource.withSession { session ->
            hent(id, session)
        }

    override fun hent(id: UUID, session: Session): Revurdering? =
        dataSource.withSession(session) { s ->
            """
                SELECT *
                FROM revurdering
                WHERE id = :id
            """.trimIndent()
                .hent(mapOf("id" to id), s) { row ->
                    row.toRevurdering(s)
                }
        }

    override fun hentEventuellTidligereAttestering(id: UUID): Attestering? =
        dataSource.withSession { session ->
            "select * from revurdering where id = :id"
                .hent(mapOf("id" to id), session) { row ->
                    row.stringOrNull("attestering")?.let {
                        objectMapper.readValue(it)
                    }
                }
        }

    override fun lagre(revurdering: Revurdering) {
        when (revurdering) {
            is OpprettetRevurdering -> lagre(revurdering)
            is BeregnetRevurdering -> lagre(revurdering)
            is SimulertRevurdering -> lagre(revurdering)
            is RevurderingTilAttestering -> lagre(revurdering)
            is IverksattRevurdering -> lagre(revurdering)
            is UnderkjentRevurdering -> lagre(revurdering)
        }
    }

    override fun oppdaterForhåndsvarsel(id: UUID, forhåndsvarsel: Forhåndsvarsel) {
        dataSource.withSession { session ->
            """
                UPDATE
                    revurdering r
                SET
                    forhåndsvarsel = to_json(:forhandsvarsel::json)
                WHERE
                    r.id = :id
            """.trimIndent()
                .oppdatering(
                    mapOf(
                        "id" to id,
                        "forhandsvarsel" to serialize(ForhåndsvarselDto.from(forhåndsvarsel)),
                    ),
                    session,
                )
        }
    }

    internal fun hentRevurderingerForSak(sakId: UUID, session: Session): List<Revurdering> =
        """
            SELECT
                r.*
            FROM
                revurdering r
                INNER JOIN behandling_vedtak bv
                    ON r.vedtakSomRevurderesId = bv.vedtakId
            WHERE bv.sakid=:sakId
        """.trimIndent()
            .hentListe(mapOf("sakId" to sakId), session) {
                it.toRevurdering(session)
            }

    private fun Row.toRevurdering(session: Session): Revurdering {
        val id = uuid("id")
        val periode = string("periode").let { objectMapper.readValue<Periode>(it) }
        val opprettet = tidspunkt("opprettet")
        val tilRevurdering = vedtakRepo.hent(uuid("vedtakSomRevurderesId"), session)!! as Vedtak.EndringIYtelse
        val beregning = stringOrNull("beregning")?.let { objectMapper.readValue<PersistertBeregning>(it) }
        val simulering = stringOrNull("simulering")?.let { objectMapper.readValue<Simulering>(it) }
        val saksbehandler = string("saksbehandler")
        val oppgaveId = stringOrNull("oppgaveid")
        val attestering = stringOrNull("attestering")?.let { objectMapper.readValue<Attestering>(it) }
        val fritekstTilBrev = stringOrNull("fritekstTilBrev")
        val årsak = string("årsak")
        val begrunnelse = string("begrunnelse")
        val revurderingsårsak = Revurderingsårsak.create(
            årsak = årsak,
            begrunnelse = begrunnelse,
        )
        val skalFøreTilBrevutsending = boolean("skalFøreTilBrevutsending")
        val forhåndsvarsel = stringOrNull("forhåndsvarsel")?.let {
            objectMapper.readValue<ForhåndsvarselDto>(it).toDomain()
        }

        val behandlingsinformasjon: Behandlingsinformasjon = deserialize(string("behandlingsinformasjon"))

        return when (RevurderingsType.valueOf(string("revurderingsType"))) {
            RevurderingsType.UNDERKJENT_INNVILGET -> UnderkjentRevurdering.Innvilget(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = Saksbehandler(saksbehandler),
                beregning = beregning!!,
                simulering = simulering!!,
                oppgaveId = OppgaveId(oppgaveId!!),
                attestering = attestering!! as Attestering.Underkjent,
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel!!,
                behandlingsinformasjon = behandlingsinformasjon,
            )
            RevurderingsType.UNDERKJENT_OPPHØRT -> UnderkjentRevurdering.Opphørt(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = Saksbehandler(saksbehandler),
                beregning = beregning!!,
                simulering = simulering!!,
                oppgaveId = OppgaveId(oppgaveId!!),
                attestering = attestering!! as Attestering.Underkjent,
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel!!,
                behandlingsinformasjon = behandlingsinformasjon,
            )
            RevurderingsType.IVERKSATT_INNVILGET -> IverksattRevurdering.Innvilget(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                beregning = beregning!!,
                simulering = simulering!!,
                attestering = attestering!! as Attestering.Iverksatt,
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel!!,
                behandlingsinformasjon = behandlingsinformasjon,
            )
            RevurderingsType.IVERKSATT_OPPHØRT -> IverksattRevurdering.Opphørt(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = Saksbehandler(saksbehandler),
                beregning = beregning!!,
                simulering = simulering!!,
                oppgaveId = OppgaveId(oppgaveId!!),
                attestering = attestering!! as Attestering.Iverksatt,
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel!!,
                behandlingsinformasjon = behandlingsinformasjon,
            )
            RevurderingsType.TIL_ATTESTERING_INNVILGET -> RevurderingTilAttestering.Innvilget(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                beregning = beregning!!,
                simulering = simulering!!,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel!!,
                behandlingsinformasjon = behandlingsinformasjon,
            )
            RevurderingsType.TIL_ATTESTERING_OPPHØRT -> RevurderingTilAttestering.Opphørt(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                beregning = beregning!!,
                simulering = simulering!!,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel!!,
                behandlingsinformasjon = behandlingsinformasjon,
            )
            RevurderingsType.SIMULERT_INNVILGET -> SimulertRevurdering.Innvilget(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                beregning = beregning!!,
                simulering = simulering!!,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel,
                behandlingsinformasjon = behandlingsinformasjon,
            )
            RevurderingsType.SIMULERT_OPPHØRT -> SimulertRevurdering.Opphørt(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                beregning = beregning!!,
                simulering = simulering!!,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel,
                behandlingsinformasjon = behandlingsinformasjon,
            )
            RevurderingsType.BEREGNET_INNVILGET -> BeregnetRevurdering.Innvilget(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                beregning = beregning!!,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel,
                behandlingsinformasjon = behandlingsinformasjon,
            )
            RevurderingsType.BEREGNET_OPPHØRT -> BeregnetRevurdering.Opphørt(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                beregning = beregning!!,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel,
                behandlingsinformasjon = behandlingsinformasjon,
            )
            RevurderingsType.OPPRETTET -> OpprettetRevurdering(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel,
                behandlingsinformasjon = behandlingsinformasjon,
            )
            RevurderingsType.BEREGNET_INGEN_ENDRING -> BeregnetRevurdering.IngenEndring(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                beregning = beregning!!,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel,
                behandlingsinformasjon = behandlingsinformasjon,
            )
            RevurderingsType.TIL_ATTESTERING_INGEN_ENDRING -> RevurderingTilAttestering.IngenEndring(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                beregning = beregning!!,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel,
                skalFøreTilBrevutsending = skalFøreTilBrevutsending,
                behandlingsinformasjon = behandlingsinformasjon,
            )
            RevurderingsType.IVERKSATT_INGEN_ENDRING -> IverksattRevurdering.IngenEndring(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                beregning = beregning!!,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel,
                attestering = attestering!! as Attestering.Iverksatt,
                skalFøreTilBrevutsending = skalFøreTilBrevutsending,
                behandlingsinformasjon = behandlingsinformasjon,
            )
            RevurderingsType.UNDERKJENT_INGEN_ENDRING -> UnderkjentRevurdering.IngenEndring(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                beregning = beregning!!,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel,
                attestering = attestering!! as Attestering.Underkjent,
                skalFøreTilBrevutsending = skalFøreTilBrevutsending,
                behandlingsinformasjon = behandlingsinformasjon,
            )
        }
    }

    private fun lagre(revurdering: OpprettetRevurdering) =
        dataSource.withSession { session ->
            (
                """
                    insert into revurdering (
                        id,
                        opprettet,
                        periode,
                        beregning,
                        simulering,
                        saksbehandler,
                        oppgaveId,
                        revurderingsType,
                        attestering,
                        vedtakSomRevurderesId,
                        fritekstTilBrev,
                        årsak,
                        begrunnelse,
                        forhåndsvarsel,
                        behandlingsinformasjon
                    ) values (
                        :id,
                        :opprettet,
                        to_json(:periode::json),
                        null,
                        null,
                        :saksbehandler,
                        :oppgaveId,
                        '${RevurderingsType.OPPRETTET}',
                        null,
                        :vedtakSomRevurderesId,
                        :fritekstTilBrev,
                        :arsak,
                        :begrunnelse,
                        to_json(:forhandsvarsel::json),
                        to_json(:behandlingsinformasjon::json)
                    )
                        ON CONFLICT(id) do update set
                        id=:id,
                        opprettet=:opprettet,
                        periode=to_json(:periode::json),
                        beregning=null,
                        simulering=null,
                        saksbehandler=:saksbehandler,
                        oppgaveId=:oppgaveId,
                        revurderingsType='${RevurderingsType.OPPRETTET}',
                        attestering=null,
                        vedtakSomRevurderesId=:vedtakSomRevurderesId,
                        fritekstTilBrev=:fritekstTilBrev,
                        årsak=:arsak,
                        begrunnelse=:begrunnelse,
                        forhåndsvarsel=to_json(:forhandsvarsel::json),
                        behandlingsinformasjon=to_json(:behandlingsinformasjon::json)
                """.trimIndent()
                ).oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "periode" to objectMapper.writeValueAsString(revurdering.periode),
                    "opprettet" to revurdering.opprettet,
                    "saksbehandler" to revurdering.saksbehandler.navIdent,
                    "oppgaveId" to revurdering.oppgaveId.toString(),
                    "vedtakSomRevurderesId" to revurdering.tilRevurdering.id,
                    "fritekstTilBrev" to revurdering.fritekstTilBrev,
                    "arsak" to revurdering.revurderingsårsak.årsak.toString(),
                    "begrunnelse" to revurdering.revurderingsårsak.begrunnelse.toString(),
                    "forhandsvarsel" to revurdering.forhåndsvarsel?.let { objectMapper.writeValueAsString(ForhåndsvarselDto.from(it)) },
                    "behandlingsinformasjon" to objectMapper.writeValueAsString(revurdering.behandlingsinformasjon),
                ),
                session,
            )
        }

    private fun lagre(revurdering: BeregnetRevurdering) =
        dataSource.withSession { session ->
            (
                """
                    update
                        revurdering
                    set
                        beregning = to_json(:beregning::json),
                        simulering = null,
                        revurderingsType = :revurderingsType,
                        saksbehandler = :saksbehandler,
                        årsak = :arsak,
                        begrunnelse = :begrunnelse,
                        behandlingsinformasjon = to_json(:behandlingsinformasjon::json)
                    where
                        id = :id
                """.trimIndent()
                ).oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "saksbehandler" to revurdering.saksbehandler.navIdent,
                    "beregning" to objectMapper.writeValueAsString(revurdering.beregning),
                    "revurderingsType" to when (revurdering) {
                        is BeregnetRevurdering.IngenEndring -> RevurderingsType.BEREGNET_INGEN_ENDRING
                        is BeregnetRevurdering.Innvilget -> RevurderingsType.BEREGNET_INNVILGET
                        is BeregnetRevurdering.Opphørt -> RevurderingsType.BEREGNET_OPPHØRT
                    },
                    "arsak" to revurdering.revurderingsårsak.årsak.toString(),
                    "begrunnelse" to revurdering.revurderingsårsak.begrunnelse.toString(),
                    "behandlingsinformasjon" to objectMapper.writeValueAsString(revurdering.behandlingsinformasjon),
                ),
                session,
            )
        }

    private fun lagre(revurdering: SimulertRevurdering) =
        dataSource.withSession { session ->
            (
                """
                    update
                        revurdering
                    set
                        saksbehandler = :saksbehandler,
                        beregning = to_json(:beregning::json),
                        simulering = to_json(:simulering::json),
                        revurderingsType = :revurderingsType,
                        årsak = :arsak,
                        begrunnelse =:begrunnelse,
                        forhåndsvarsel = to_json(:forhandsvarsel::json),
                        behandlingsinformasjon = to_json(:behandlingsinformasjon::json)
                    where
                        id = :id
                """.trimIndent()
                ).oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "saksbehandler" to revurdering.saksbehandler.navIdent,
                    "beregning" to objectMapper.writeValueAsString(revurdering.beregning),
                    "simulering" to objectMapper.writeValueAsString(revurdering.simulering),
                    "arsak" to revurdering.revurderingsårsak.årsak.toString(),
                    "begrunnelse" to revurdering.revurderingsårsak.begrunnelse.toString(),
                    "revurderingsType" to when (revurdering) {
                        is SimulertRevurdering.Innvilget -> RevurderingsType.SIMULERT_INNVILGET
                        is SimulertRevurdering.Opphørt -> RevurderingsType.SIMULERT_OPPHØRT
                    },
                    "forhandsvarsel" to revurdering.forhåndsvarsel?.let { objectMapper.writeValueAsString(ForhåndsvarselDto.from(it)) },
                    "behandlingsinformasjon" to objectMapper.writeValueAsString(revurdering.behandlingsinformasjon),
                ),
                session,
            )
        }

    private fun lagre(revurdering: RevurderingTilAttestering) =
        dataSource.withSession { session ->
            (
                """
                    update
                        revurdering
                    set
                        saksbehandler = :saksbehandler,
                        beregning = to_json(:beregning::json),
                        simulering = to_json(:simulering::json),
                        oppgaveId = :oppgaveId,
                        fritekstTilBrev = :fritekstTilBrev,
                        årsak = :arsak,
                        begrunnelse =:begrunnelse,
                        revurderingsType = :revurderingsType,
                        skalFøreTilBrevutsending = :skalFoereTilBrevutsending,
                        behandlingsinformasjon = to_json(:behandlingsinformasjon::json)
                    where
                        id = :id
                """.trimIndent()
                ).oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "saksbehandler" to revurdering.saksbehandler.navIdent,
                    "beregning" to objectMapper.writeValueAsString(revurdering.beregning),
                    "simulering" to when (revurdering) {
                        is RevurderingTilAttestering.IngenEndring -> null
                        is RevurderingTilAttestering.Innvilget -> objectMapper.writeValueAsString(revurdering.simulering)
                        is RevurderingTilAttestering.Opphørt -> objectMapper.writeValueAsString(revurdering.simulering)
                    },
                    "oppgaveId" to revurdering.oppgaveId.toString(),
                    "fritekstTilBrev" to revurdering.fritekstTilBrev,
                    "arsak" to revurdering.revurderingsårsak.årsak.toString(),
                    "begrunnelse" to revurdering.revurderingsårsak.begrunnelse.toString(),
                    "revurderingsType" to when (revurdering) {
                        is RevurderingTilAttestering.IngenEndring -> RevurderingsType.TIL_ATTESTERING_INGEN_ENDRING
                        is RevurderingTilAttestering.Innvilget -> RevurderingsType.TIL_ATTESTERING_INNVILGET
                        is RevurderingTilAttestering.Opphørt -> RevurderingsType.TIL_ATTESTERING_OPPHØRT
                    },
                    "skalFoereTilBrevutsending" to when (revurdering) {
                        is RevurderingTilAttestering.IngenEndring -> revurdering.skalFøreTilBrevutsending
                        is RevurderingTilAttestering.Innvilget -> true
                        is RevurderingTilAttestering.Opphørt -> true
                    },
                    "behandlingsinformasjon" to objectMapper.writeValueAsString(revurdering.behandlingsinformasjon),
                ),
                session,
            )
        }

    private fun lagre(revurdering: IverksattRevurdering) =
        dataSource.withSession { session ->
            (
                """
                    update
                        revurdering
                    set
                        saksbehandler = :saksbehandler,
                        beregning = to_json(:beregning::json),
                        simulering = to_json(:simulering::json),
                        oppgaveId = :oppgaveId,
                        attestering = to_json(:attestering::json),
                        årsak = :arsak,
                        begrunnelse =:begrunnelse,
                        revurderingsType = :revurderingsType,
                        behandlingsinformasjon = to_json(:behandlingsinformasjon::json)
                    where
                        id = :id
                """.trimIndent()
                ).oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "saksbehandler" to revurdering.saksbehandler.navIdent,
                    "beregning" to objectMapper.writeValueAsString(revurdering.beregning),
                    "simulering" to when (revurdering) {
                        is IverksattRevurdering.IngenEndring -> null
                        is IverksattRevurdering.Innvilget -> objectMapper.writeValueAsString(revurdering.simulering)
                        is IverksattRevurdering.Opphørt -> objectMapper.writeValueAsString(revurdering.simulering)
                    },
                    "oppgaveId" to revurdering.oppgaveId.toString(),
                    "attestering" to objectMapper.writeValueAsString(revurdering.attestering),
                    "arsak" to revurdering.revurderingsårsak.årsak.toString(),
                    "begrunnelse" to revurdering.revurderingsårsak.begrunnelse.toString(),
                    "revurderingsType" to when (revurdering) {
                        is IverksattRevurdering.IngenEndring -> RevurderingsType.IVERKSATT_INGEN_ENDRING
                        is IverksattRevurdering.Innvilget -> RevurderingsType.IVERKSATT_INNVILGET
                        is IverksattRevurdering.Opphørt -> RevurderingsType.IVERKSATT_OPPHØRT
                    },
                    "behandlingsinformasjon" to objectMapper.writeValueAsString(revurdering.behandlingsinformasjon),
                ),
                session,
            )
        }

    private fun lagre(revurdering: UnderkjentRevurdering) =
        dataSource.withSession { session ->
            (
                """
                    update
                        revurdering
                    set
                        oppgaveId = :oppgaveId,
                        attestering = to_json(:attestering::json),
                        årsak = :arsak,
                        begrunnelse =:begrunnelse,
                        revurderingsType = :revurderingsType,
                        behandlingsinformasjon = to_json(:behandlingsinformasjon::json)
                    where
                        id = :id
                """.trimIndent()
                ).oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "oppgaveId" to revurdering.oppgaveId.toString(),
                    "attestering" to objectMapper.writeValueAsString(revurdering.attestering),
                    "arsak" to revurdering.revurderingsårsak.årsak.toString(),
                    "begrunnelse" to revurdering.revurderingsårsak.begrunnelse.toString(),
                    "revurderingsType" to when (revurdering) {
                        is UnderkjentRevurdering.IngenEndring -> RevurderingsType.UNDERKJENT_INGEN_ENDRING
                        is UnderkjentRevurdering.Innvilget -> RevurderingsType.UNDERKJENT_INNVILGET
                        is UnderkjentRevurdering.Opphørt -> RevurderingsType.UNDERKJENT_OPPHØRT
                    },
                    "behandlingsinformasjon" to objectMapper.writeValueAsString(revurdering.behandlingsinformasjon),
                ),
                session,
            )
        }

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
    )
    @JsonSubTypes(
        JsonSubTypes.Type(value = ForhåndsvarselDto.IngenForhåndsvarsel::class, name = "IngenForhåndsvarsel"),
        JsonSubTypes.Type(value = ForhåndsvarselDto.Sendt::class, name = "Sendt"),
        JsonSubTypes.Type(value = ForhåndsvarselDto.Besluttet::class, name = "Besluttet"),
    )
    sealed class ForhåndsvarselDto {
        object IngenForhåndsvarsel : ForhåndsvarselDto()

        data class Sendt(
            val journalpostId: JournalpostId,
            val brevbestillingId: BrevbestillingId?,
        ) : ForhåndsvarselDto()

        data class Besluttet(
            val journalpostId: JournalpostId,
            val brevbestillingId: BrevbestillingId?,
            val valg: BeslutningEtterForhåndsvarsling,
            val begrunnelse: String,
        ) : ForhåndsvarselDto()

        fun toDomain(): Forhåndsvarsel =
            when (this) {
                is Sendt -> Forhåndsvarsel.SkalForhåndsvarsles.Sendt(
                    journalpostId = journalpostId,
                    brevbestillingId = brevbestillingId,
                )
                is IngenForhåndsvarsel -> Forhåndsvarsel.IngenForhåndsvarsel
                is Besluttet -> Forhåndsvarsel.SkalForhåndsvarsles.Besluttet(
                    journalpostId = journalpostId,
                    brevbestillingId = brevbestillingId,
                    valg = valg,
                    begrunnelse = begrunnelse,
                )
            }

        companion object {
            fun from(forhåndsvarsel: Forhåndsvarsel) =
                when (forhåndsvarsel) {
                    is Forhåndsvarsel.SkalForhåndsvarsles.Sendt -> Sendt(
                        journalpostId = forhåndsvarsel.journalpostId,
                        brevbestillingId = forhåndsvarsel.brevbestillingId,
                    )
                    is Forhåndsvarsel.IngenForhåndsvarsel -> IngenForhåndsvarsel
                    is Forhåndsvarsel.SkalForhåndsvarsles.Besluttet -> Besluttet(
                        journalpostId = forhåndsvarsel.journalpostId,
                        brevbestillingId = forhåndsvarsel.brevbestillingId,
                        valg = forhåndsvarsel.valg,
                        begrunnelse = forhåndsvarsel.begrunnelse,
                    )
                }
        }
    }
}
