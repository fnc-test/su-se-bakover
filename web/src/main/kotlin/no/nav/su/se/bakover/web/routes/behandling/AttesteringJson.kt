package no.nav.su.se.bakover.web.routes.behandling

internal data class AttesteringJson(
    val attestant: String,
    val underkjennelse: UnderkjennelseJson?
)

internal data class UnderkjennelseJson(
    val grunn: String,
    val kommentar: String,
)
