/** https://github.com/seratch/kotliquery/blob/master/LICENSE
(The MIT License)

Copyright (c) 2015 - Kazuhiro Sera

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
'Software'), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED 'AS IS', WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package no.nav.su.se.bakover.database

import kotliquery.Connection
import kotliquery.Parameter
import kotliquery.Query
import kotliquery.Row
import kotliquery.action.ListResultQueryAction
import kotliquery.action.NullableResultQueryAction
import kotliquery.action.UpdateQueryAction
import kotliquery.param
import kotliquery.sqlType
import kotliquery.using
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.revurdering.RevurderingsType
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.vedtak.VedtakType
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.math.BigDecimal
import java.net.URL
import java.sql.PreparedStatement
import java.sql.Statement
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import javax.sql.DataSource

open class Session(
    open val connection: Connection,
    open val returnGeneratedKeys: Boolean = true,
    open val autoGeneratedKeys: List<String> = listOf(),
    var transactional: Boolean = false
) : AutoCloseable {

    override fun close() {
        transactional = false
        connection.close()
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    private inline fun <reified T> PreparedStatement.setTypedParam(idx: Int, param: Parameter<T>) {
        if (param.value == null) {
            this.setNull(idx, param.sqlType())
        } else {
            setParam(idx, param.value)
        }
    }

    private fun PreparedStatement.setParam(idx: Int, v: Any?) {
        if (v == null) {
            this.setObject(idx, null)
        } else {
            when (v) {
                is String -> this.setString(idx, v)
                is Byte -> this.setByte(idx, v)
                is Boolean -> this.setBoolean(idx, v)
                is Int -> this.setInt(idx, v)
                is Long -> this.setLong(idx, v)
                is Short -> this.setShort(idx, v)
                is Double -> this.setDouble(idx, v)
                is Float -> this.setFloat(idx, v)
                is ZonedDateTime -> this.setTimestamp(idx, Timestamp.from(v.toInstant()))
                is OffsetDateTime -> this.setTimestamp(idx, Timestamp.from(v.toInstant()))
                is Instant -> this.setTimestamp(idx, Timestamp.from(v))
                is Tidspunkt -> this.setTimestamp(idx, Timestamp.from(v.instant))
                is LocalDateTime -> this.setTimestamp(
                    idx,
                    Timestamp(org.joda.time.LocalDateTime.parse(v.toString()).toDate().time)
                )
                is LocalDate -> this.setDate(
                    idx,
                    java.sql.Date(org.joda.time.LocalDate.parse(v.toString()).toDate().time)
                )
                is LocalTime -> this.setTime(
                    idx,
                    java.sql.Time(org.joda.time.LocalTime.parse(v.toString()).toDateTimeToday().millis)
                )
                is java.util.Date -> this.setTimestamp(idx, Timestamp(v.time))
                is java.sql.Timestamp -> this.setTimestamp(idx, v)
                is java.sql.Time -> this.setTime(idx, v)
                is java.sql.Date -> this.setTimestamp(idx, Timestamp(v.time))
                is java.sql.SQLXML -> this.setSQLXML(idx, v)
                is ByteArray -> this.setBytes(idx, v)
                is InputStream -> this.setBinaryStream(idx, v)
                is BigDecimal -> this.setBigDecimal(idx, v)
                is java.sql.Array -> this.setArray(idx, v)
                is URL -> this.setURL(idx, v)
                is UUID30 -> this.setString(idx, v.toString())
                is Fnr -> this.setString(idx, v.toString())
                is NavIdentBruker -> this.setString(idx, v.navIdent)
                is OppgaveId -> this.setString(idx, v.toString())
                is BrevbestillingId -> this.setString(idx, v.toString())
                is JournalpostId -> this.setString(idx, v.toString())
                is VedtakType -> this.setString(idx, v.toString())
                is Utbetalingslinje.LinjeStatus -> this.setString(idx, v.toString())
                is RevurderingsType -> this.setString(idx, v.toString())
                else -> this.setObject(idx, v)
            }
        }
    }

    fun populateParams(query: Query, stmt: PreparedStatement): PreparedStatement {
        if (query.replacementMap.isNotEmpty()) {
            query.replacementMap.forEach { paramName, occurrences ->
                occurrences.forEach {
                    stmt.setTypedParam(it + 1, query.paramMap[paramName].param())
                }
            }
        } else {
            query.params.forEachIndexed { index, value ->
                stmt.setTypedParam(index + 1, value.param())
            }
        }

        return stmt
    }

    fun createPreparedStatement(query: Query): PreparedStatement {
        val stmt = if (returnGeneratedKeys) {
            if (connection.driverName == "oracle.jdbc.driver.OracleDriver") {
                connection.underlying.prepareStatement(query.cleanStatement, autoGeneratedKeys.toTypedArray())
            } else {
                connection.underlying.prepareStatement(query.cleanStatement, Statement.RETURN_GENERATED_KEYS)
            }
        } else {
            connection.underlying.prepareStatement(query.cleanStatement)
        }

        return populateParams(query, stmt)
    }

    private fun <A> rows(query: Query, extractor: (Row) -> A?): List<A> {
        return using(createPreparedStatement(query)) { stmt ->
            using(stmt.executeQuery()) { rs ->
                val rows = Row(rs).map { row -> extractor.invoke(row) }
                rows.filter { r -> r != null }.map { r -> r!! }.toList()
            }
        }
    }

    private fun warningForTransactionMode() {
        if (transactional) {
            logger.warn("Use TransactionalSession instead. The `tx` of `session.transaction { tx -> ... }`")
        }
    }

    fun <A> single(query: Query, extractor: (Row) -> A?): A? {
        warningForTransactionMode()
        val rs = rows(query, extractor)
        return if (rs.size > 0) rs.first() else null
    }

    fun <A> list(query: Query, extractor: (Row) -> A?): List<A> {
        warningForTransactionMode()
        return rows(query, extractor).toList()
    }

    fun update(query: Query): Int {
        warningForTransactionMode()
        return using(createPreparedStatement(query)) { stmt ->
            stmt.executeUpdate()
        }
    }

    fun run(action: UpdateQueryAction): Int {
        return update(action.query)
    }

    fun <A> run(action: ListResultQueryAction<A>): List<A> {
        return list(action.query, action.extractor)
    }

    fun <A> run(action: NullableResultQueryAction<A>): A? {
        return single(action.query, action.extractor)
    }

    fun <A> transaction(operation: (TransactionalSession) -> A): A {
        try {
            connection.begin()
            transactional = true
            val tx = TransactionalSession(connection, returnGeneratedKeys, autoGeneratedKeys)
            val result: A = operation.invoke(tx)
            connection.commit()
            return result
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            transactional = false
        }
    }
}

class TransactionalSession(
    override val connection: Connection,
    override val returnGeneratedKeys: Boolean = false,
    override val autoGeneratedKeys: List<String> = listOf()
) : Session(connection, returnGeneratedKeys, autoGeneratedKeys)

fun sessionOf(dataSource: DataSource, returnGeneratedKey: Boolean = false): Session {
    return Session(Connection(dataSource.connection), returnGeneratedKey)
}
