package no.nav.su.se.bakover.institusjonsopphold.database

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.InstitusjonsoppholdKilde
import no.nav.su.se.bakover.institusjonsopphold.database.InstitusjonsoppholdKildeDb.Companion.toDb
import org.junit.jupiter.api.Test

class InstitusjonsoppholdKildeDbTest {

    @Test
    fun `mapper domenet type til riktig db type`() {
        InstitusjonsoppholdKilde.INST.toDb() shouldBe InstitusjonsoppholdKildeDb.INST
        InstitusjonsoppholdKilde.IT.toDb() shouldBe InstitusjonsoppholdKildeDb.IT
        InstitusjonsoppholdKilde.KDI.toDb() shouldBe InstitusjonsoppholdKildeDb.KDI
        InstitusjonsoppholdKilde.APPBRK.toDb() shouldBe InstitusjonsoppholdKildeDb.APPBRK
    }

    @Test
    fun `mapper db type til riktig domene type`() {
        InstitusjonsoppholdKildeDb.INST.toDomain() shouldBe InstitusjonsoppholdKilde.INST
        InstitusjonsoppholdKildeDb.IT.toDomain() shouldBe InstitusjonsoppholdKilde.IT
        InstitusjonsoppholdKildeDb.KDI.toDomain() shouldBe InstitusjonsoppholdKilde.KDI
        InstitusjonsoppholdKildeDb.APPBRK.toDomain() shouldBe InstitusjonsoppholdKilde.APPBRK
    }
}
