package no.nav.su.se.bakover.client.oppgave

internal data class EndreOppgaveRequest(
    val id: Long,
    val versjon: Int,
    val status: String
)
