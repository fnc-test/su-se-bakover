package no.nav.su.se.bakover.database

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class MigrationsPostgresTest {

    @Test
    fun `migreringer skal kjøre på en tom database`() {
        EmbeddedDatabase.instance().also {
            clean(it)
            val migrations = Flyway(it, "postgres").migrate()
            assertTrue(migrations > 0)
        }
    }

    @Test
    fun `migreringer skal ikke kjøre flere ganger`() {
        EmbeddedDatabase.instance().also {
            clean(it)
            assertTrue(Flyway(it, "postgres").migrate() > 0)
            assertEquals(0, Flyway(it, "postgres").migrate())
        }
    }
}
