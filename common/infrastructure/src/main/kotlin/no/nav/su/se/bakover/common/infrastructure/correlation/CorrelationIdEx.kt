package no.nav.su.se.bakover.common.infrastructure.correlation

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.CorrelationId
import org.slf4j.LoggerFactory
import org.slf4j.MDC

const val CorrelationIdHeader = "X-Correlation-ID"

private val log = LoggerFactory.getLogger("CorrelationIdEx.kt")

/**
 * Denne er kun tenkt brukt fra coroutines/jobber/consumers som ikke er knyttet til Ktor.
 * Ktor håndterer dette ved hjelp av [io.ktor.server.plugins.callid.callIdMdc]
 *
 * Overskriver den nåværende key-value paret i MDC.
 * Fjerner keyen etter body() har kjørt ferdig.
 */
fun withCorrelationId(body: (CorrelationId) -> Unit) {
    runBlocking {
        withCorrelationIdSuspend(body)
    }
}

/** Henter [CorrelationIdHeader] fra MDC dersom den finnes eller genererer en ny og legger den på MDC. */
fun getOrCreateCorrelationIdFromThreadLocal(): CorrelationId {
    return getCorrelationIdFromThreadLocal() ?: (
        CorrelationId.generate().also {
            MDC.put(CorrelationIdHeader, it.toString())
        }
        )
}

suspend fun withCorrelationIdSuspend(body: suspend (CorrelationId) -> Unit) {
    val correlationId = CorrelationId.generate()
    try {
        MDC.put(CorrelationIdHeader, correlationId.toString())
        body(correlationId)
    } finally {
        Either.catch {
            MDC.remove(CorrelationIdHeader)
        }.onLeft {
            log.error("En ukjent feil skjedde når vi prøvde fjerne $CorrelationIdHeader fra MDC.", it)
        }
    }
}

private fun getCorrelationIdFromThreadLocal(): CorrelationId? {
    return MDC.get(CorrelationIdHeader)?.let { CorrelationId(it) } ?: null.also {
        log.error(
            "Mangler '$CorrelationIdHeader' på MDC. Er dette et asynk-kall? Da må det settes manuelt, så tidlig som mulig.",
            RuntimeException("Genererer en stacktrace for enklere debugging."),
        )
    }
}
