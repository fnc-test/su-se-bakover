package no.nav.su.se.bakover.sak

import com.google.gson.JsonObject
import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import javax.sql.DataSource

interface Repository {
    fun opprettSak(fnr: String): Long
    fun hentSak(fnr: String): Sak?
    fun hentSak(id: Long): Sak?
    fun hentAlleSaker(): List<Sak>
    fun hentSoknadForPerson(fnr: String): Søknad?
    fun hentSøknad(søknadId: Long): Søknad?
    fun lagreSøknad(søknadJson: JsonObject, sakId: Long): Long?
    fun hentSøknaderForSak(sakId: Long): List<Søknad>
}

internal class PostgresRepository(
        private val dataSource: DataSource
) : Repository {
    private fun String.oppdatering(params: Map<String, Any>):Long? =
        using(sessionOf(dataSource, returnGeneratedKey = true)) {
            it.run(queryOf(this, params).asUpdateAndReturnGeneratedKey)
        }

    private fun <T> String.hent(params: Map<String, Any> = emptyMap(), rowMapping: (Row) -> T): T? = using(sessionOf(dataSource)) {
        it.run(queryOf(this, params).map { row -> rowMapping(row) }.asSingle)
    }

    private fun <T> String.hentListe(params: Map<String, Any> = emptyMap(), rowMapping: (Row) -> T): List<T> = using(sessionOf(dataSource)) {
        it.run(queryOf(this, params).map { row -> rowMapping(row) }.asList)
    }

    override fun hentSak(fnr: String): Sak? = "select * from sak where fnr=:fnr".hent(mapOf("fnr" to fnr), Row::somSak)
    override fun hentSak(id: Long): Sak? = "select * from sak where id=:id".hent(mapOf("id" to id), Row::somSak)
    override fun hentAlleSaker(): List<Sak> = "select * from sak".hentListe(rowMapping = Row::somSak)
    override fun opprettSak(fnr: String): Long = "insert into sak (fnr) values (:fnr::varchar)".oppdatering(mapOf("fnr" to fnr))!!
    // TODO: List not single
    override fun hentSoknadForPerson(fnr: String): Søknad? = "SELECT * FROM søknad WHERE json#>>'{personopplysninger,fnr}'=:fnr".hent(mapOf("fnr" to fnr), Row::somSøknad)
    override fun hentSøknaderForSak(sakId: Long): List<Søknad> = "SELECT * FROM søknad WHERE sakId=:sakId".hentListe(mapOf("sakId" to sakId), Row::somSøknad)
    override fun hentSøknad(søknadId: Long): Søknad? = "SELECT * FROM søknad WHERE id=:id".hent(mapOf("id" to søknadId), Row::somSøknad)
    override fun lagreSøknad(søknadJson: JsonObject, sakId: Long): Long? = "INSERT INTO søknad (json, sakId) VALUES (to_json(:soknad::json), :sakId)".oppdatering(mapOf("soknad" to søknadJson.toString(), "sakId" to sakId))

}
private fun Row.somSøknad() = Søknad(id = long("id"), søknadJson = string("json"), sakId = long("sakId"))
private fun Row.somSak(): Sak = Sak(id = long("id"),  fnr = string("fnr"))
