package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.Either
import no.nav.su.se.bakover.db.Repository
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

val nySakId = Random.nextLong()
val nySøknadId = Random.nextLong()
val førstegangssøker = "01010112345"
val eksisterendeSakId = Random.nextLong()
val andregangssøker = "09090912345"
val søknadstekst = """
    {
    "hei": "jeg skulle gjerne hatt litt supplerende stønad",
    "erdetmulig": false,
    "men": -1
    }
""".trimIndent()

internal class SakTest {

    @Test
    fun `factory må klare å lage en ny sak fra et fnr, når det ikke finnes en sak fra før`() {
        val nySakTest = AssertNySakOpprettet()
        SakFactory(
                repository = TomtRepository(),
                søknadObservers = emptyList(),
                sakObservers = listOf(nySakTest))
            .forFnr(førstegangssøker)

        assertTrue(nySakTest.nySak, "Ny sak event skulle blitt trigget")
    }

    @Test
    fun `factory må klare å hente en sak fra repository, og så legge på en ny søknad`() {
        val repository = RepositoryForNySøknad()
        val nySøknadTest = AssertNySøknadMottat()
        SakFactory(
                repository = repository,
                søknadObservers = listOf(nySøknadTest),
                sakObservers = emptyList())
            .forFnr(andregangssøker)
            .nySøknad(søknadstekst)

        assertTrue(nySøknadTest.nySøknad, "Søknad mottatt event skulle blitt trigget")
    }

    @Test
    fun `factory må levere en Error ved henting av sak med en identitet som ikke finnes`() {
        val eitherSakOrNothing = SakFactory(
            repository = TomtRepository(),
            søknadObservers = emptyList(),
            sakObservers = emptyList()
        ).forId(nySakId)
        when(eitherSakOrNothing) {
            is Either.Error -> assertTrue(true)
            is Either.Value -> fail("Skulle ikke ha funnet en sak")
        }
    }

    @Test
    fun `factory må klare å hente en sak fra repository basert på en identitet`() {
        val eitherSakOrNothing = SakFactory(
            repository = RepositoryForNySøknad(),
            søknadObservers = emptyList(),
            sakObservers = emptyList()
        ).forId(eksisterendeSakId)
        when(eitherSakOrNothing) {
            is Either.Error -> fail("Skulle ikke ha fått feil fra søknadFactory")
            is Either.Value -> assertTrue(true)
        }
    }

}

internal class TomtRepository : Repository {
    override fun nySak(fnr: String): Long = nySakId
    override fun sakIdForFnr(fnr: String): Long? = null
    override fun nySøknad(sakId: Long, json: String): Long = nySøknadId
    override fun fnrForSakId(sakId: Long): String? = null
    override fun søknaderForSak(sakId: Long): List<Pair<Long, String>> = emptyList()
    override fun alleSaker(): List<Pair<Long, String>> = emptyList()
}

internal class RepositoryForNySøknad: Repository {
    override fun nySak(fnr: String): Long = throw RuntimeException("Skulle ikke lagre sak")
    override fun sakIdForFnr(fnr: String): Long? = eksisterendeSakId
    override fun nySøknad(sakId: Long, json: String): Long = nySøknadId
    override fun fnrForSakId(sakId: Long): String? = andregangssøker
    override fun søknaderForSak(sakId: Long): List<Pair<Long, String>> = emptyList()
    override fun alleSaker(): List<Pair<Long, String>> = emptyList()
}

internal class AssertNySakOpprettet : SakObserver {
    var nySak: Boolean = false
    override fun nySakOpprettet(event: SakObserver.NySakEvent) {
        nySak = true
        assertEquals(SakObserver.NySakEvent(fnr = førstegangssøker, id = nySakId), event)
    }
}

internal class AssertNySøknadMottat : SøknadObserver {
    var nySøknad = false
    override fun søknadMottatt(event: SøknadObserver.SøknadMottattEvent) {
        nySøknad = true
        assertEquals(SøknadObserver.SøknadMottattEvent(
            sakId = eksisterendeSakId,
            søknadId = nySøknadId,
            søknadstekst = søknadstekst
        ), event)
    }
}
