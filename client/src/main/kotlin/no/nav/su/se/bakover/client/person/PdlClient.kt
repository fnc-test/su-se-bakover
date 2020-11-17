package no.nav.su.se.bakover.client.person

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.httpPost
import no.nav.su.se.bakover.client.person.PdlData.Adresse
import no.nav.su.se.bakover.client.person.PdlData.Ident
import no.nav.su.se.bakover.client.person.PdlData.Navn
import no.nav.su.se.bakover.client.person.Variables.Companion.AKTORID
import no.nav.su.se.bakover.client.person.Variables.Companion.FOLKEREGISTERIDENT
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.filterMap
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Telefonnummer
import no.nav.su.se.bakover.domain.person.PersonOppslag.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.PersonOppslag.KunneIkkeHentePerson.FantIkkePerson
import no.nav.su.se.bakover.domain.person.PersonOppslag.KunneIkkeHentePerson.IkkeTilgangTilPerson
import no.nav.su.se.bakover.domain.person.PersonOppslag.KunneIkkeHentePerson.Ukjent
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.time.LocalDate

const val NAV_CONSUMER_TOKEN = "Nav-Consumer-Token"
const val NAV_TEMA = "Tema"
const val SUP = "SUP"

internal class PdlClient(
    private val pdlUrl: String,
    private val tokenOppslag: TokenOppslag
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val hentPersonQuery = this::class.java.getResource("/hentPerson.graphql").readText()
    private val hentIdenterQuery = this::class.java.getResource("/hentIdenter.graphql").readText()

    fun person(fnr: Fnr): Either<KunneIkkeHentePerson, PdlData> {
        return kallpdl<PersonResponseData>(fnr, hentPersonQuery).map { response ->
            val hentPerson = response.hentPerson!!
            val navn = hentPerson.navn.sortedBy {
                folkeregisteretAsMaster(it.metadata)
            }.first()
            val alleAdresser = hentAlleAdresser(
                listOf(
                    hentPerson.bostedsadresse,
                    hentPerson.oppholdsadresse,
                    hentPerson.kontaktadresse
                ).filterMap { it.firstOrNull() }
            )

            // TODO jah: Don't throw exception if we can't find this person
            PdlData(
                ident = Ident(hentIdent(response.hentIdenter!!).fnr, hentIdent(response.hentIdenter).aktørId),
                navn = Navn(
                    fornavn = navn.fornavn,
                    mellomnavn = navn.mellomnavn,
                    etternavn = navn.etternavn
                ),
                telefonnummer = hentPerson.telefonnummer.firstOrNull()?.let {
                    Telefonnummer(landskode = it.landskode, nummer = it.nummer)
                },
                adresse = alleAdresser,
                statsborgerskap = hentPerson.statsborgerskap.firstOrNull()?.land,
                kjønn = hentPerson.kjoenn.map { it.kjoenn }.firstOrNull(),
                adressebeskyttelse = hentPerson.adressebeskyttelse.firstOrNull()?.gradering,
                vergemålEllerFremtidsfullmakt = hentPerson.vergemaalEllerFremtidsfullmakt.isNotEmpty(),
                fullmakt = hentPerson.fullmakt.isNotEmpty(),
            )
        }
    }

    private fun folkeregisteretAsMaster(metadata: Metadata) = metadata.master.toLowerCase() == "freg"

    fun aktørId(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId> {
        return kallpdl<IdentResponseData>(fnr, hentIdenterQuery).map {
            hentIdent(it.hentIdenter!!).aktørId
        }
    }

    private fun hentIdent(it: HentIdenter) =
        PdlIdent(
            fnr = it.identer.first { it.gruppe == FOLKEREGISTERIDENT }.ident.let { Fnr(it) },
            aktørId = it.identer.first { it.gruppe == AKTORID }.ident.let { AktørId(it) }
        )

    private inline fun <reified T> kallpdl(fnr: Fnr, query: String): Either<KunneIkkeHentePerson, T> {
        val pdlRequest = PdlRequest(query, Variables(ident = fnr.toString()))
        val token = tokenOppslag.token()
        val (_, response, result) = "$pdlUrl/graphql".httpPost()
            .header("Authorization", MDC.get("Authorization"))
            .header(NAV_CONSUMER_TOKEN, "Bearer $token")
            .header(NAV_TEMA, SUP)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .body(objectMapper.writeValueAsString(pdlRequest))
            .responseString()
        return result.fold(
            {
                val pdlResponse: PdlResponse<T> = objectMapper.readValue(it, specializedType(T::class.java))
                if (pdlResponse.hasErrors()) {
                    håndtererPdlFeil(pdlResponse).left()
                } else {
                    pdlResponse.data.right()
                }
            },
            {
                log.error(
                    "Feil i kallet mot PDL, status:{}, body:{}",
                    response.statusCode,
                    response.body().asString("application/json")
                )
                Ukjent.left()
            }
        )
    }

    private fun <T> håndtererPdlFeil(pdlResponse: PdlResponse<T>): KunneIkkeHentePerson {
        val feil = pdlResponse.toKunneIkkeHentePerson()
        if (feil.size != 1) {
            log.error("Mer enn 1 feil fra PDL: ${pdlResponse.errors}")
            return Ukjent
        }
        if (feil.first() is Ukjent) {
            log.error("Ukjent feilresponskode fra PDL: ${pdlResponse.errors}")
        }
        return feil.first()
    }

    private fun hentAlleAdresser(adresser: List<Adressetype>): List<Adresse> {
        fun tilAdresse(adresse: Adresseformat): Adresse? {
            return when (adresse) {
                is Vegadresse -> Adresse(
                    adresselinje = "${adresse.adressenavn} ${adresse.husnummer}${adresse.husnummer}",
                    postnummer = adresse.postnummer,
                    bruksenhet = adresse.bruksenhetsnummer,
                    kommunenummer = adresse.kommunenummer,
                )
                is PostadresseIFrittFormat -> Adresse(
                    adresselinje = listOf(
                        adresse.adresselinje1,
                        adresse.adresselinje2,
                        adresse.adresselinje3
                    ).joinToString(),
                    postnummer = adresse.postnummer,
                )
                is Postboksadresse -> Adresse(
                    adresselinje = "${adresse.postbokseier}, ${adresse.postboks}",
                    postnummer = adresse.postnummer
                )
                is UkjentBosted -> null
                is Matrikkeladresse -> Adresse(
                    adresselinje = listOf("MatrikkelId: ${adresse.matrikkelId}", adresse.tilleggsnavn).joinToString(),
                    postnummer = adresse.postnummer,
                    bruksenhet = adresse.bruksenhetsnummer,
                    kommunenummer = adresse.kommunenummer
                )
                is UtenlandskAdresse -> Adresse(
                    adresselinje = listOf(
                        adresse.adressenavnNummer,
                        adresse.bygningEtasjeLeilighet,
                        adresse.bySted,
                        adresse.regionDistriktOmraade
                    ).joinToString(),
                    postnummer = adresse.postkode,
                    landkode = adresse.landkode
                )
                is UtenlandskAdresseIFrittFormat -> Adresse(
                    adresselinje = listOf(
                        adresse.adresselinje1,
                        adresse.adresselinje2,
                        adresse.adresselinje3,
                        adresse.byEllerStedsnavn
                    ).joinToString(),
                    postnummer = adresse.postkode,
                    landkode = adresse.landkode
                )
            }
        }

        return adresser.map {
            // TODO ai: Se om man kan førenkle
            when (it) {
                is Bostedsadresse -> {
                    listOf(it.vegadresse, it.matrikeladresse, it.ukjentBosted).firstOrNull()?.let { adresseformat ->
                        tilAdresse(adresseformat)
                    }
                }
                is Oppholdsadresse -> listOf(it.vegadresse, it.matrikeladresse, it.utenlandskAdresse).firstOrNull()
                    ?.let { adresseformat ->
                        tilAdresse(adresseformat)
                    }
                is Kontaktadresse -> listOf(
                    it.vegadresse,
                    it.postadresseIFrittFormat,
                    it.postboksadresse,
                    it.utenlandskAdresse,
                    it.utenlandskAdresseIFrittFormat,
                ).firstOrNull()?.let { adresseformat -> tilAdresse(adresseformat) }
            }
        }.filterNotNull()
    }

    private fun specializedType(clazz: Class<*>) =
        objectMapper.typeFactory.constructParametricType(PdlResponse::class.java, clazz)
}

data class PdlResponse<T>(
    val data: T,
    val errors: List<PdlError>?
) {
    fun hasErrors() = !errors.isNullOrEmpty()

    fun toKunneIkkeHentePerson(): List<KunneIkkeHentePerson> {
        return errors.orEmpty().map {
            resolveError(it.extensions.code)
        }
    }

    private fun resolveError(code: String) = when (code.toLowerCase()) {
        "not_found" -> FantIkkePerson
        "unauthorized" -> IkkeTilgangTilPerson
        else -> Ukjent
    }
}

data class PdlError(
    val message: String,
    val path: List<String>,
    val extensions: PdlExtension
)

data class PdlExtension(
    val code: String
)

data class IdentResponseData(
    val hentIdenter: HentIdenter?
)

data class PersonResponseData(
    val hentPerson: HentPerson?,
    val hentIdenter: HentIdenter?
)

data class HentPerson(
    val navn: List<NavnResponse>,
    val telefonnummer: List<TelefonnummerResponse>,
    val bostedsadresse: List<Bostedsadresse>,
    val kontaktadresse: List<Kontaktadresse>,
    val oppholdsadresse: List<Oppholdsadresse>,
    val statsborgerskap: List<Statsborgerskap>,
    val kjoenn: List<Kjønn>,
    val adressebeskyttelse: List<Adressebeskyttelse>,
    val vergemaalEllerFremtidsfullmakt: List<VergemaalEllerFremtidsfullmakt>,
    val fullmakt: List<Fullmakt>
)

data class NavnResponse(
    val etternavn: String,
    val fornavn: String,
    val mellomnavn: String?,
    val metadata: Metadata
)

data class TelefonnummerResponse(
    val landskode: String,
    val nummer: String,
    val prioritet: Int
)

sealed class Adressetype()
data class Bostedsadresse(
    val vegadresse: Vegadresse?,
    val ukjentBosted: UkjentBosted?,
    val matrikeladresse: Matrikkeladresse?
) : Adressetype()

data class Kontaktadresse(
    val vegadresse: Vegadresse?,
    val postadresseIFrittFormat: PostadresseIFrittFormat?,
    val postboksadresse: Postboksadresse?,
    val utenlandskAdresse: UtenlandskAdresse?,
    val utenlandskAdresseIFrittFormat: UtenlandskAdresseIFrittFormat?,
) : Adressetype()

data class Oppholdsadresse(
    val vegadresse: Vegadresse?,
    val matrikeladresse: Matrikkeladresse?,
    val utenlandskAdresse: UtenlandskAdresse?,
) : Adressetype()

data class Statsborgerskap(
    val land: String,
    val gyldigFraOgMed: LocalDate?,
    val gyldigTilOgMed: LocalDate?
)

sealed class Adresseformat()
data class Vegadresse(
    val husnummer: String?,
    val husbokstav: String?,
    val adressenavn: String?,
    val kommunenummer: String?,
    val postnummer: String?,
    val bruksenhetsnummer: String?
) : Adresseformat()

data class PostadresseIFrittFormat(
    val adresselinje1: String?,
    val adresselinje2: String?,
    val adresselinje3: String?,
    val postnummer: String?
) : Adresseformat()

data class Postboksadresse(
    val postbokseier: String?,
    val postboks: String?,
    val postnummer: String?
) : Adresseformat()

data class UkjentBosted(
    val bostedskommune: String
) : Adresseformat()

data class Matrikkeladresse(
    val matrikkelId: Long?,
    val bruksenhetsnummer: String?,
    val tilleggsnavn: String?,
    val postnummer: String?,
    val kommunenummer: String?,
) : Adresseformat()

data class UtenlandskAdresse(
    val adressenavnNummer: String?,
    val bygningEtasjeLeilighet: String?,
    val postboksNummerNavn: String?,
    val postkode: String?,
    val bySted: String?,
    val regionDistriktOmraade: String?,
    val landkode: String,
) : Adresseformat()

data class UtenlandskAdresseIFrittFormat(
    val adresselinje1: String?,
    val adresselinje2: String?,
    val adresselinje3: String?,
    val postkode: String?,
    val byEllerStedsnavn: String?,
    val landkode: String,
) : Adresseformat()

data class Metadata(
    val opplysningsId: String?,
    val master: String,
    val historisk: Boolean
)

data class HentIdenter(
    val identer: List<Id>
)

data class Id(
    val gruppe: String,
    val ident: String
)

data class Kjønn(
    val kjoenn: String
)

data class Adressebeskyttelse(
    val gradering: String
)

data class VergemaalEllerFremtidsfullmakt(
    val type: String?,
    val vergeEllerFullmektig: VergeEllerFullmektig
) {

    data class VergeEllerFullmektig(
        val motpartsPersonident: String
    )
}

data class Fullmakt(
    val motpartsRolle: FullmaktsRolle,
    val gyldigFraOgMed: LocalDate,
    val gyldigTilOgMed: LocalDate,
) {
    enum class FullmaktsRolle {
        FULLMAKTSGIVER,
        FULLMEKTIG
    }
}
