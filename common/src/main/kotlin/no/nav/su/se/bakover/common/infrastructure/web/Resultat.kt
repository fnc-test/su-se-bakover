package no.nav.su.se.bakover.common.infrastructure.web

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import no.nav.su.se.bakover.common.infrastructure.web.Resultat.Companion.json
import no.nav.su.se.bakover.common.serialize

/* forstår seg på hvordan et resultat med en melding blir til en http-response */
data class Resultat private constructor(
    val httpCode: HttpStatusCode,
    private val json: String,
    private val contentType: ContentType,
) {
    init {
        require(httpCode in HttpStatusCode.allStatusCodes) { "Unknown http status code:$httpCode" }
    }

    suspend fun svar(call: ApplicationCall) =
        call.respondText(contentType = contentType, status = httpCode, text = json)

    companion object {
        fun json(httpCode: HttpStatusCode, json: String): Resultat =
            Resultat(httpCode, json, contentType = ContentType.Application.Json)

        fun okJson() = json(HttpStatusCode.OK, """{"status": "OK"}""")
    }
}

fun HttpStatusCode.errorJson(message: String, code: String): Resultat {
    return json(this, serialize(ErrorJson(message, code)))
}

fun HttpStatusCode.errorJson(errors: List<ErrorJson>): Resultat {
    return json(this, serialize(errors))
}

suspend fun ApplicationCall.svar(resultat: Resultat) = resultat.svar(this)